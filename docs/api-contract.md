# Contrato de API — sentinel-auth-api (rascunho)

> **Isto é um rascunho/contrato inicial, não a especificação OpenAPI final.** Formaliza, antes da implementação, os 4 endpoints já decididos em [`docs/prd.md` (seção 4)](prd.md#4-escopo-do-v100--stable-authentication-api) e [`docs/architecture.md`](architecture.md#fluxo-de-autenticação-e-ciclo-de-vida-de-tokens), para alinhar o formato de request/response antes de escrever código. A spec OpenAPI real será **gerada automaticamente pelo springdoc a partir do código**, conforme os endpoints forem implementados nas fases `v0.2.0`–`v0.4.0` do [roadmap](../README.md#roadmap) (ver também [PRD, item 7 — Swagger/OpenAPI](prd.md#4-escopo-do-v100--stable-authentication-api)). Quando isso acontecer, este documento deve ser considerado superado pela documentação viva (`/swagger-ui.html`), não mantido em paralelo.

Este documento **não introduz nenhum endpoint, campo ou decisão de arquitetura nova** — apenas formaliza o que já está registrado em `docs/prd.md`, `docs/architecture.md` e nos ADRs referenciados. Onde um detalhe de contrato ainda não tem decisão registrada, isso é sinalizado explicitamente na seção [Pontos em aberto](#pontos-em-aberto), em vez de inventado.

## Convenções gerais

- **Prefixo de versionamento**: todos os endpoints estão sob `/api/v1` ([ADR-0002](adr/0002-uri-based-api-versioning.md)).
- **Content-Type**: requests e responses de sucesso usam `application/json`; respostas de erro usam `application/problem+json` (`ProblemDetail`, [ADR-0003](adr/0003-rfc9457-error-format.md)).
- **Autenticação**: endpoints que exigem um usuário autenticado recebem o access token via header `Authorization: Bearer <accessToken>`.
- **Formato de erro**: todas as respostas de erro seguem RFC 9457, com os campos `type`, `title`, `status`, `detail`, `instance` ([ADR-0003](adr/0003-rfc9457-error-format.md)). Exemplo genérico:

  ```json
  {
    "type": "about:blank",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Credenciais inválidas.",
    "instance": "/api/v1/auth/login"
  }
  ```

  `type` usa `about:blank` (default do `ProblemDetail` do Spring quando nenhum tipo customizado é definido) — nenhuma URI própria de catálogo de erros foi decidida até o momento; ver [Pontos em aberto](#pontos-em-aberto).
- **Validação de entrada**: campos obrigatórios/formato são validados via Jakarta Bean Validation nos DTOs ([ADR-0004](adr/0004-bean-validation-input.md)); violações resultam em `400` no formato acima.
- Nenhum endpoint retorna senha em texto plano ou hash de senha em nenhuma response, em nenhuma circunstância.

## Endpoints

### 1. Register

`POST /api/v1/auth/register`

Cria um novo usuário. Corresponde ao item 1 da tabela de escopo do [PRD](prd.md#4-escopo-do-v100--stable-authentication-api).

**Request**

```json
{
  "email": "ana.silva@example.com",
  "password": "SenhaForte123!"
}
```

Senha em **texto plano** no request — nunca hash (o hash Argon2id é gerado no service, ver [`docs/technologies/argon2id.md`](technologies/argon2id.md)).

**Response de sucesso — `201 Created`**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "ana.silva@example.com",
  "createdAt": "2026-09-02T14:30:00Z"
}
```

Nunca inclui senha/hash, nem outros campos internos do modelo `User` (`enabled`, `locked`, `emailVerified`, `roles`) além do que for necessário confirmar o cadastro — esses campos não fazem parte deste rascunho de response até serem necessários (YAGNI). O tipo exato do `id` (UUID vs. identificador numérico) segue o exemplo de migração em [`docs/technologies/flyway.md`](technologies/flyway.md), mas não é uma decisão fixada por ADR.

**Erros possíveis**

| Status | Cenário |
|---|---|
| `400` | Corpo inválido (e-mail malformado, senha ausente, etc.) — Bean Validation |
| `409` | E-mail já cadastrado |

Exemplo (`409`):

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "E-mail já cadastrado.",
  "instance": "/api/v1/auth/register"
}
```

### 2. Login

`POST /api/v1/auth/login`

Autentica um usuário e emite o par de tokens. Corresponde ao item 2 do [PRD](prd.md#4-escopo-do-v100--stable-authentication-api).

**Request**

```json
{
  "email": "ana.silva@example.com",
  "password": "SenhaForte123!"
}
```

**Response de sucesso — `200 OK`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "8f14e45f-ceea-467e-bd42-9f9b3b1a1a1a",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` em segundos, refletindo os 15 minutos de vida do access token ([`docs/architecture.md`](architecture.md#tempo-de-vida-dos-tokens)). O formato exato do `refreshToken` (JWT assinado vs. token opaco) ainda não está decidido — ver ponto em aberto correspondente em [`docs/technologies/jjwt.md`](technologies/jjwt.md#quando-usar) e na seção [Pontos em aberto](#pontos-em-aberto) abaixo.

**Erros possíveis**

| Status | Cenário |
|---|---|
| `400` | Corpo inválido — Bean Validation |
| `401` | E-mail ou senha inválidos — resposta **não distingue** qual dos dois falhou, para evitar enumeração de usuários (ver [`docs/security-threats.md`](security-threats.md) e [`docs/integrations/argon2id-spring-boot.md`](integrations/argon2id-spring-boot.md)) |

Exemplo (`401`):

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Credenciais inválidas.",
  "instance": "/api/v1/auth/login"
}
```

### 3. Refresh

`POST /api/v1/auth/refresh`

Emite um novo par de tokens a partir de um refresh token válido, invalidando o anterior (rotação a cada uso). Corresponde ao item 3 do [PRD](prd.md#4-escopo-do-v100--stable-authentication-api).

**Request**

```json
{
  "refreshToken": "8f14e45f-ceea-467e-bd42-9f9b3b1a1a1a"
}
```

O servidor aceita o refresh token vindo do corpo (mobile) **ou** de um cookie `httpOnly` (web) — o que estiver presente na requisição; se ambos vierem, o cookie tem precedência; se nenhum vier, é tratado como token inválido (mesmo `401` abaixo). Ver [ADR-0009](adr/0009-dual-channel-refresh-token-delivery.md).

**Response de sucesso — `200 OK`**

Mesmo formato do login — novo par de tokens, retornado tanto no corpo quanto via `Set-Cookie` ([ADR-0009](adr/0009-dual-channel-refresh-token-delivery.md)):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "b2e6a9d0-1c3f-4a2e-8f7d-2d9a6e5c4b3a",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Erros possíveis**

| Status | Cenário |
|---|---|
| `401` | Refresh token inválido, expirado, já usado, ou ausente do corpo e do cookie (rotação — ver [`docs/architecture.md` — Persistência e revogação](architecture.md#persistência-e-revogação)) |

Exemplo (`401`):

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Refresh token inválido, expirado ou já utilizado.",
  "instance": "/api/v1/auth/refresh"
}
```

### 4. Logout

`POST /api/v1/auth/logout`

Revoga um refresh token associado ao usuário autenticado. Corresponde ao item 4 do [PRD](prd.md#4-escopo-do-v100--stable-authentication-api).

**Request**

```
Authorization: Bearer <accessToken>
```

```json
{
  "refreshToken": "8f14e45f-ceea-467e-bd42-9f9b3b1a1a1a"
}
```

O critério de aceite do PRD exige um "refresh token válido associado ao usuário autenticado" — por isso o request combina o access token (autenticação, via header) com o refresh token a revogar (corpo ou cookie, mesma regra de precedência do endpoint de refresh — [ADR-0009](adr/0009-dual-channel-refresh-token-delivery.md)).

**Response de sucesso — `204 No Content`**

Sem corpo — apenas confirmação via status code, já que não há dado relevante a devolver após a revogação. A resposta também limpa o cookie do refresh token (`Set-Cookie` com `Max-Age=0`).

**Erros possíveis**

| Status | Cenário |
|---|---|
| `401` | Access token ausente/inválido, ou refresh token informado inválido/não pertencente ao usuário autenticado |

Exemplo (`401`):

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Refresh token inválido ou não associado ao usuário autenticado.",
  "instance": "/api/v1/auth/logout"
}
```

## Pontos em aberto

Detalhes de contrato que **não têm decisão registrada** em `docs/architecture.md`, no PRD ou em nenhum ADR até o momento. Não foram decididos aqui — ficam sinalizados para serem resolvidos quando o código for de fato escrito, principalmente na fase `v0.3.0 — Authentication Core` do roadmap:

- **Formato do refresh token** (JWT assinado com `jti` vs. token opaco/`UUID`) — já sinalizado como ponto em aberto em [`docs/technologies/jjwt.md`](technologies/jjwt.md#quando-usar).
- **Tipo exato do identificador (`id`) do usuário** — UUID (como no exemplo ilustrativo de [`docs/technologies/flyway.md`](technologies/flyway.md)) ou identificador numérico autoincrementado.
- **URIs de `type` customizadas** para o catálogo de erros RFC 9457 (hoje `about:blank` em todos os exemplos, por ser o default do `ProblemDetail`).
- **Formato exato do corpo de erro `400` de validação** (se inclui lista de violações por campo, via propriedade adicional do `ProblemDetail`, ou apenas o `detail` genérico).

## Referências

- [`docs/prd.md`](prd.md) — escopo funcional e critérios de aceite do `v1.0.0`.
- [`docs/architecture.md`](architecture.md) — modelo de domínio, fluxo de autenticação e ciclo de vida de tokens.
- [ADR-0002](adr/0002-uri-based-api-versioning.md) — versionamento via URI.
- [ADR-0003](adr/0003-rfc9457-error-format.md) — formato de erro RFC 9457.
- [ADR-0004](adr/0004-bean-validation-input.md) — validação de entrada via Bean Validation.
- [`docs/security-threats.md`](security-threats.md) — notas de threat model, incluindo a decisão de não diferenciar erros de login para evitar enumeração de usuários.
