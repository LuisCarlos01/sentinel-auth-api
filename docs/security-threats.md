# Notas de threat model — sentinel-auth-api

Lista enxuta das principais ameaças relevantes para uma API de autenticação e como cada uma é tratada no `sentinel-auth-api` — mitigada por uma decisão já registrada, ou aceita conscientemente como risco por ora. **Não é um STRIDE formal completo**: o projeto segue KISS/YAGNI, e este documento cobre o que é relevante para o escopo atual, não um catálogo exaustivo de ameaças. Serve como referência rápida, não como ensaio — cada item aponta para onde a decisão real está documentada, em vez de reexplicá-la.

## Ameaças e mitigações

### Senha em texto plano / vazamento de credenciais armazenadas

**Mitigação**: senha nunca é persistida em texto plano — hashing via Argon2id (`Argon2PasswordEncoder`), com parâmetros default do Spring Security. Mesmo em caso de vazamento do banco, o custo de quebrar os hashes é alto.

Ver [`docs/technologies/argon2id.md`](technologies/argon2id.md).

### Brute force / credential stuffing no login

**Status**: risco aceito conscientemente até a fase `v0.5.0 — Quality & Security` do roadmap. Sem rate limiting, o endpoint `login` fica exposto a tentativas repetidas de credenciais entre a v0.1.0 e a v0.5.0 — decisão de sequenciamento já registrada, não reexplicada aqui.

Ver [ADR-0005](adr/0005-defer-rate-limiting.md).

### Enumeração de usuários

**Mitigação**: a resposta de erro do `login` não diferencia "e-mail não existe" de "senha incorreta" — ambos os casos retornam o mesmo `401` genérico no formato RFC 9457, sem detalhar qual credencial falhou.

Ver [`docs/integrations/argon2id-spring-boot.md`](integrations/argon2id-spring-boot.md) e o exemplo de erro em [`docs/api-contract.md` — Login](api-contract.md#2-login).

### Roubo/replay de refresh token

**Mitigação**: refresh token tem rotação a cada uso — cada `refresh` invalida o token anterior — e é persistido em tabela no PostgreSQL, permitindo revogação real no `logout` (ao contrário de um esquema puramente stateless). Um token roubado e reutilizado após o legítimo dono já ter feito refresh (ou logout) deixa de ser válido.

Ver [`docs/architecture.md` — Fluxo de autenticação e ciclo de vida de tokens](architecture.md#fluxo-de-autenticação-e-ciclo-de-vida-de-tokens).

### XSS roubando token no navegador

**Mitigação**: no cliente web, o access token é mantido **apenas em memória**, nunca em `localStorage`/`sessionStorage` — um script malicioso injetado via XSS não tem uma fonte persistente e acessível via JS de onde ler o token.

Ver [`docs/architecture.md` — Armazenamento por canal do cliente](architecture.md#armazenamento-por-canal-do-cliente).

### CSRF via cookie do refresh token

**Mitigação**: o refresh token do cliente web fica em cookie `httpOnly`, `Secure`, `SameSite=Strict` ([ADR-0009](adr/0009-dual-channel-refresh-token-delivery.md)). O atributo `SameSite` mitiga CSRF porque impede o navegador de enviar o cookie em requisições disparadas por um site de terceiro (ex.: um `<form>` ou `fetch` malicioso hospedado fora da origem da aplicação) — sem o cookie anexado, a requisição forjada não consegue autenticar a chamada a `refresh`/`logout` em nome da vítima. `Strict` (em vez de `Lax`) foi escolhido porque o projeto não tem nenhum fluxo legítimo de navegação top-level cross-site que dependeria do cookie ser enviado.

Ver [`docs/architecture.md` — Armazenamento por canal do cliente](architecture.md#armazenamento-por-canal-do-cliente) e [ADR-0009](adr/0009-dual-channel-refresh-token-delivery.md).

### SQL Injection

**Mitigação**: persistência via JPA/Hibernate com queries parametrizadas (derivadas de método/JPQL) — nenhuma query nativa concatenando string de entrada do usuário está prevista no desenho atual.

Ver [`docs/architecture.md` — Stack técnica](architecture.md#stack-técnica).

### Segredos vazando no repositório

**Mitigação**: segredos (chave de assinatura JWT, credenciais de banco) nunca são commitados — geridos via variáveis de ambiente (`.env` local, não versionado) e GitHub Secrets em CI/CD.

Ver [`docs/architecture.md` — Segurança operacional e segredos](architecture.md#segurança-operacional-e-segredos).

## Referências

- [`docs/architecture.md`](architecture.md)
- [`docs/adr/0005-defer-rate-limiting.md`](adr/0005-defer-rate-limiting.md)
- [`docs/technologies/argon2id.md`](technologies/argon2id.md)
- [`docs/integrations/argon2id-spring-boot.md`](integrations/argon2id-spring-boot.md)
- [`docs/api-contract.md`](api-contract.md)
