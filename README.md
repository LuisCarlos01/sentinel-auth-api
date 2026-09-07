# sentinel-auth-api
Production-inspired authentication API with Spring Boot, JWT, Refresh Tokens, RBAC, PostgreSQL, Docker, tests and CI/CD.

## Getting Started

Pré-requisito: Docker e Docker Compose instalados (não é necessário Java/Maven local — o `Dockerfile` já cuida do build da aplicação).

1. Clone o repositório:

   ```bash
   git clone <repo-url>
   cd sentinel-auth-api
   ```

2. Suba a aplicação e o Postgres:

   ```bash
   docker compose up
   ```

   O Docker Compose já tem defaults funcionais para as variáveis de ambiente. Se quiser customizá-las, copie `.env.example` para `.env` (`cp .env.example .env`) e ajuste os valores.

   As migrations Flyway (schema `users`, entre outras) rodam automaticamente na inicialização da aplicação — não é necessário nenhum comando manual.

3. Acesse:
   - API: `http://localhost:8080`
   - Health check: `http://localhost:8080/actuator/health`
   - Swagger UI: `http://localhost:8080/swagger-ui.html` — endpoints de autenticação (`/api/v1/auth/register`, `/login`, `/refresh`, `/logout`) e `GET /api/v1/users` (restrito ao papel `ADMIN`)

4. Para rodar os testes localmente (requer JDK 25 via `mise` e Docker rodando — os testes de integração sobem seu próprio Postgres via Testcontainers, sem precisar do `docker compose` completo):

   ```bash
   mise exec -- ./mvnw test
   ```

## Roadmap

- [x] v0.1.0 — Project Bootstrap
  - [x] Phase 0 — Scope and architecture
  - [x] Phase 1 — Spring Boot foundation

- [x] v0.2.0 — User Persistence
- [x] v0.3.0 — Authentication Core
- [x] v0.4.0 — Authorization & Token Lifecycle
- [ ] v0.5.0 — Quality & Security
- [ ] v1.0.0 — Stable Authentication API
- [ ] v2.0.0 — OAuth2/OIDC
- [ ] v3.0.0 — AWS Cognito Integration

## License

[MIT](LICENSE)
