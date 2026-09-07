# 0009 — Entrega do refresh token por corpo JSON e cookie simultaneamente

## Status

Accepted

## Contexto

`docs/architecture.md` já registra que o cliente web guarda o refresh token em cookie `httpOnly`, `Secure`, `SameSite`, enquanto o cliente mobile usa o armazenamento seguro do próprio sistema operacional (Keychain/Keystore) — mas essa é uma decisão de *armazenamento no cliente*, não de como a API entrega/recebe o valor. `docs/api-contract.md` sinalizava isso como ponto em aberto desde o rascunho do contrato.

O projeto não tem um BFF (Backend for Frontend) nem gateway intermediário: `docs/architecture.md` afirma explicitamente que a API "é consumida diretamente por um frontend web e por um app mobile". Sem uma camada intermediária para traduzir o formato por canal, a própria API precisa decidir como entregar o token de forma que sirva os dois canais sem exigir que o cliente se identifique explicitamente (ex.: um header de tipo de cliente).

## Decisão

`login` e `refresh` retornam o refresh token em **dois lugares ao mesmo tempo** na mesma resposta: no corpo JSON (campo `refreshToken`, como já implementado em `login`) e via `Set-Cookie` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age` alinhado ao TTL de 7 dias do refresh token, nome do cookie `refreshToken`). O cliente web usa o cookie e ignora o campo do corpo; o cliente mobile usa o campo do corpo e ignora o cookie.

Nas requisições de `refresh` e `logout`, o servidor aceita o refresh token vindo do cookie **ou** do corpo JSON — o que estiver presente. Se ambos vierem na mesma requisição, o **cookie tem precedência** (é a fonte "oficial" do canal web; um corpo presente junto com cookie é tratado como sobra do outro canal, não como uma segunda fonte de verdade). Se nenhum dos dois vier, o servidor trata como token inválido, reaproveitando o mesmo `401` já definido em `docs/api-contract.md` para refresh token inválido/expirado/já usado — sem introduzir um novo caso de erro `400` só para "token ausente".

`logout` também limpa o cookie na resposta (`Set-Cookie` com `Max-Age=0`, mesmo `Path`), além de revogar a linha correspondente em `refresh_tokens` (ADR-0008) — evita que um cookie de um token já revogado no banco fique "morto" no navegador até expirar sozinho.

`SameSite=Strict`: não existe, neste projeto, nenhum fluxo legítimo de navegação top-level cross-site que dependeria de `Lax` (ex.: redirect de OAuth de terceiros) — a API é consumida via `fetch`/XHR de mesma origem. `Strict` é estritamente mais restritivo contra CSRF sem custo funcional conhecido no desenho atual.

Esta decisão exige retrofit do endpoint `login` já implementado (hoje só retorna o refresh token no corpo) para também setar o cookie.

### Alternativas consideradas

- **Só corpo JSON, sempre** (cookie fora do escopo da API): mais simples, mas contradiz a decisão já registrada em `architecture.md` de que o canal web usa cookie `httpOnly` de verdade — exigiria reabrir e reverter aquele documento sem motivo técnico novo.
- **Detecção de canal via header explícito** (ex.: `X-Client-Type: web|mobile`) para decidir se responde com cookie ou corpo: descartado por adicionar um contrato novo (o cliente precisa mandar o header certo) para resolver o mesmo problema que o dual-channel resolve sem exigir nada do cliente além de usar o que já recebe.
- **`SameSite=Lax`**: descartado — nenhum fluxo do projeto depende de enviar o cookie em navegação cross-site; `Strict` é mais seguro sem trade-off funcional identificado.

## Consequências

**Positivas**

- Um único endpoint serve os dois canais sem exigir que o cliente se identifique — o servidor responde com os dois formatos e cada cliente usa o que precisa.
- `refresh`/`logout` funcionam de forma idêntica seja qual for a origem da requisição, sem branch de código por tipo de cliente.
- `SameSite=Strict` + `HttpOnly` + `Secure` fecham a mitigação de CSRF já esperada em `docs/security-threats.md`, que estava com o valor de `SameSite` em aberto.

**Trade-offs aceitos**

- A resposta de `login`/`refresh` expõe o refresh token em texto plano no corpo JSON mesmo quando o cliente é web e vai usar o cookie — um XSS que leia a resposta da requisição de rede (não o cookie em si, que é `httpOnly`) ainda consegue capturar o valor do corpo. Risco aceito conscientemente: o mesmo XSS já teria como abusar da sessão de outras formas, e a alternativa (omitir o corpo quando detectar canal web) exigiria a detecção de canal já descartada acima.
- Precedência do cookie sobre o corpo quando os dois vêm juntos é uma regra implícita que precisa estar documentada (este ADR) para não ser redescoberta por tentativa e erro no futuro.
