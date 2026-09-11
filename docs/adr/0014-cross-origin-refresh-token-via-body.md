# 0014 — Cliente web usa o corpo JSON (não o cookie) para refresh em topologia cross-origin

## Status

Accepted

## Contexto

`sentinel-auth-web` foi implantado no Vercel (`https://sentinel-auth-web-2znh.vercel.app`), sem domínio próprio. `sentinel-auth-api` roda em `https://52-14-171-76.sslip.io` (ADR-0013). Os dois hosts não compartilham um domínio registrável em comum — a requisição do frontend pro backend é **cross-site**, não apenas cross-origin same-site.

ADR-0009 já fixou o cookie do refresh token como `HttpOnly`, `Secure`, `SameSite=Strict`, entregue **simultaneamente** ao valor em texto plano no corpo JSON da resposta de `login`/`refresh` (dual-channel, pensado originalmente para servir web e mobile com o mesmo endpoint). `SameSite=Strict` é aplicado pelo navegador **independente de CORS**: mesmo com `Access-Control-Allow-Origin`/`Access-Control-Allow-Credentials` corretos (ADR-0012) e a requisição do frontend usando `credentials: 'include'`, o navegador não anexa um cookie `Strict` a uma requisição cross-site. `login`/`register` continuam funcionando (o `Set-Cookie` da resposta é aceito normalmente), mas qualquer chamada subsequente de `refresh` feita pelo frontend nunca carrega o cookie de volta — reproduzido ao vivo contra a implantação real pelo time do frontend, que reportou isso via handoff.

O handoff do frontend descrevia o problema como não resolvível sem uma de três mudanças: domínio compartilhado, relaxar `SameSite` para `None`, ou reconsiderar o transporte do refresh token. Nenhuma delas é necessária: o handoff partia de uma premissa factualmente incorreta (documentada no ADR-0002 do próprio `sentinel-auth-web`) de que o refresh token só existe como cookie. Isso não é verdade — o dual-channel do ADR-0009 já entrega o valor em texto plano no corpo JSON desde a implementação original, pensado justamente para um cliente que não pode depender de cookie (o caso mobile). Confirmado ao vivo: a resposta de `POST /api/v1/auth/login` contra a instância real inclui `"refreshToken": "..."` no corpo, não só o `Set-Cookie`.

O dono do projeto descartou as outras duas alternativas antes desta correção: não tem domínio próprio pra colocar os dois hosts sob o mesmo registrável, e não quer relaxar `SameSite` (enfraqueceria a postura CSRF que ADR-0009 escolheu deliberadamente com `Strict`).

## Decisão

**Nenhuma mudança de código na API.** O mecanismo que resolve isso já existe desde ADR-0009. Para a topologia cross-origin atual (frontend Vercel sem domínio próprio + API em `sslip.io`), o cliente web passa a se comportar, na prática, como o cliente mobile já se comporta: usa o valor do **corpo JSON**, não o cookie, para os campos de `refresh`/`logout`.

O cookie continua sendo setado pela API normalmente (nenhum motivo pra removê-lo — é inofensivo, e mantém a porta aberta para um futuro same-site sem precisar reverter nada aqui). O ajuste é inteiramente do lado do frontend: guardar o `refreshToken` do corpo (em memória, nunca `localStorage`/`sessionStorage`) e enviá-lo explicitamente no corpo de `POST /api/v1/auth/refresh`/`logout`, em vez de depender do cookie sendo anexado automaticamente pelo navegador.

### Alternativas consideradas

- **Mesmo domínio registrável** (API em `api.<domínio-do-frontend>`, ADR-0013-style): descartado — dono do projeto não tem domínio próprio para o frontend, e resolveria só se ambos os lados tivessem domínio (nenhum tem).
- **Relaxar para `SameSite=None` + `Secure`**: descartado — enfraquece deliberadamente a proteção CSRF que ADR-0009 escolheu com `Strict`, e exigiria um controle compensatório (ex.: double-submit token) que não existe hoje. Decisão explícita do dono do projeto de não seguir por aqui.
- **Reconsiderar por completo o transporte do refresh token**: descartado — o dual-channel de ADR-0009 já resolve isso sem exigir mudança nenhuma; era, na verdade, um mal-entendido sobre o que a API já entrega, não uma lacuna real de design.

## Consequências

**Positivas**

- Zero mudança de código/endpoint na API — o dual-channel de ADR-0009 já contemplava exatamente este uso.
- Não abre mão da postura `SameSite=Strict`/CSRF já decidida.
- Não introduz custo de domínio.
- Corrige um mal-entendido documentado (ADR-0002 do `sentinel-auth-web`) que fazia o problema parecer maior do que é.

**Trade-offs aceitos**

- O cliente web passa a expor o refresh token em texto plano na resposta de rede (já um trade-off aceito em ADR-0009 para o corpo JSON, agora **efetivamente utilizado** pelo web em vez de só existir para mobile) — mesma mitigação (nenhuma nova) já discutida em ADR-0009.
- Se um dia o frontend ganhar domínio próprio no mesmo registrável da API (ou vice-versa), o cookie volta a funcionar sem mudança de código na API — mas o frontend precisaria ser atualizado para voltar a confiar nele.

## Referências

- [ADR-0009](0009-dual-channel-refresh-token-delivery.md) — mecanismo dual-channel que esta decisão reaproveita sem alteração.
- [ADR-0012](0012-cors-configuration.md) — CORS por si só não resolve isto; `SameSite` é aplicado independentemente.
- [ADR-0013](0013-https-via-elastic-ip-sslip-caddy.md) — API sem domínio próprio, mesma restrição do lado do frontend que motiva esta decisão.
- Handoff externo de `sentinel-auth-web` (bug reproduzido ao vivo contra a implantação real em produção; ADR-0002 do frontend continha a premissa incorreta corrigida aqui).
