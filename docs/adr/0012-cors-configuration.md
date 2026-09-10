# 0012 — Configuração de CORS para clientes browser

## Status

Accepted

## Contexto

`docs/architecture.md` já registra que a API é consumida diretamente por um frontend web (`sentinel-auth-web`) e um app mobile, sem BFF/gateway intermediário. O app mobile não roda em um contexto de origem — CORS não se aplica a ele. Já um cliente browser está sujeito à mesma-origem por padrão: sem cabeçalhos `Access-Control-Allow-*` corretos, o navegador bloqueia a resposta antes que o JavaScript do frontend consiga lê-la.

Antes desta decisão, nenhuma configuração de CORS existia no projeto — qualquer chamada `fetch`/XHR de `sentinel-auth-web` falharia no preflight.

ADR-0009 já fixou que `login`/`refresh` entregam o refresh token também via cookie `HttpOnly`, `Secure`, `SameSite=Strict`. Isso é o fator decisivo desta decisão: CORS com cookie exige `Access-Control-Allow-Credentials: true` na resposta, o que por sua vez **proíbe** `Access-Control-Allow-Origin: *` (a especificação Fetch não permite combinar wildcard com credentials) — a decisão de origem não podia ser "liberar tudo".

O projeto `sentinel-auth-web` ainda não tem scaffold (`package.json`) no momento desta decisão — framework e porta de dev não estão fixados.

## Decisão

CORS habilitado no `SecurityFilterChain` (`SecurityConfig`), escopado a `/api/v1/**` — os únicos endpoints consumidos por um cliente browser; `/actuator/**` e `/swagger-ui`/`/v3/api-docs` ficam fora do escopo de CORS (não são chamados por JavaScript de outra origem).

- **Origens permitidas**: lista configurável via property `sentinel.cors.allowed-origins` (env var `SENTINEL_CORS_ALLOWED_ORIGINS`), formato comma-separated, **sem valor default em produção**. Falhar fechado (nenhuma origem liberada) até a porta/domínio real de `sentinel-auth-web` ser decidida é mais seguro que chutar um valor comum (`localhost:5173`/`:3000`) que pode nem ser o real.
- **Métodos**: `GET`, `POST` — únicos usados pelos 5 endpoints existentes (`register`, `login`, `refresh`, `logout`, `GET /users`).
- **Headers**: `Authorization` (Access token, bearer) e `Content-Type`.
- **Credentials**: `true`, obrigatório pelo cookie de refresh token (ADR-0009). A configuração valida isso já na subida da aplicação (`CorsConfiguration.validateAllowCredentials()`), falhando fast se `allowed-origins` vier com `*` em vez de só falhar na primeira requisição real.

### Alternativas consideradas

- **Wildcard (`*`) como origem**: descartado — inválido pela própria especificação Fetch quando combinado com `allowCredentials=true`, e o navegador rejeitaria a resposta de qualquer forma.
- **Chutar a porta de dev do frontend (`5173`/`3000`) como default em `application.yml`**: descartado — `sentinel-auth-web` ainda não tem `package.json`; um chute errado ficaria mascarado como "configurado" até alguém notar que CORS falha silenciosamente contra a porta real.
- **CORS global (`/**`)**: descartado — vazaria para `/actuator/health` e o Swagger UI, superfícies que não precisam de CORS (não chamadas por JS cross-origin) e que, no caso do Actuator, são normalmente tratadas como superfície só-operador.

## Consequências

**Positivas**

- `sentinel-auth-web` passa a conseguir chamar a API de outra origem sem o navegador bloquear a resposta, uma vez que `SENTINEL_CORS_ALLOWED_ORIGINS` seja configurado com a origem real.
- Falhar fechado por padrão evita expor a API a qualquer origem por omissão — cada ambiente (dev, produção) precisa declarar explicitamente quem pode chamá-la.
- Escopo restrito a `/api/v1/**` evita CORS desnecessário em superfícies operacionais/documentação.

**Trade-offs aceitos**

- Sem `SENTINEL_CORS_ALLOWED_ORIGINS` configurado, a API funciona normalmente para clientes não-browser (mobile, `curl`, Postman) mas qualquer chamada de um frontend browser falha no preflight — isso é esperado até a origem real de `sentinel-auth-web` ser decidida e configurada, não um bug.
- A lista de origens permitidas precisa ser mantida manualmente conforme `sentinel-auth-web` evolui (dev, staging, produção) — sem esse update, um ambiente novo do frontend simplesmente não funciona contra a API; não há descoberta automática de origem.

## Referências

- [ADR-0009](0009-dual-channel-refresh-token-delivery.md) — cookie `HttpOnly`/`Secure`/`SameSite=Strict` do refresh token, motivo de `allowCredentials=true` aqui.
- `docs/architecture.md` — consumo direto por frontend web e app mobile, sem BFF/gateway.
