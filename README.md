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

## Deployment

Instância de demonstração pública (AWS EC2, deploy manual — ver [ADR-0011](docs/adr/0011-aws-ec2-manual-deploy.md)):

- API: `http://<pendente — atualizar após o deploy>`
- Swagger UI: `http://<pendente>/swagger-ui.html`

> Sem HTTPS/domínio próprio nesta primeira entrega (limitação conhecida e documentada em ADR-0011).

### Fazer seu próprio deploy (opcional)

Quer rodar sua própria instância na AWS em vez de só usar a de demonstração acima? Resumo rápido (passo a passo completo em [`docs/deployment.md`](docs/deployment.md)):

1. Suba uma EC2 `t3.micro` (Amazon Linux 2023), com o security group liberando `22` (SSH, só chave) e `80`.
2. Instale Docker + o plugin `docker compose` na instância.
3. Clone o repositório, copie `.env.example` para `.env` e ajuste `JWT_SIGNING_KEY`/`POSTGRES_PASSWORD` com valores reais.
4. Adicione um `docker-compose.override.yml` só na instância, mapeando `80:8080`.
5. `docker compose up --build -d`.

## Roadmap

- [x] v0.1.0 — Project Bootstrap
  - [x] Phase 0 — Scope and architecture
  - [x] Phase 1 — Spring Boot foundation

- [x] v0.2.0 — User Persistence
- [x] v0.3.0 — Authentication Core
- [x] v0.4.0 — Authorization & Token Lifecycle
- [x] v0.5.0 — Quality & Security
- [ ] v1.0.0 — Stable Authentication API
- [ ] v2.0.0 — OAuth2/OIDC
- [ ] v3.0.0 — AWS Cognito Integration

## License

[MIT](LICENSE)
