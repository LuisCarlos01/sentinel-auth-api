# Arquitetura — sentinel-auth-api

Este documento consolida as decisões de arquitetura tomadas na **Phase 0 — Scope and architecture** (`v0.1.0 — Project Bootstrap`, ver [README](../README.md)). Decisões com raciocínio próprio mais extenso foram registradas como ADRs em [`docs/adr/`](adr/README.md) e são referenciadas aqui; este documento cobre o restante do desenho e serve como ponto único de consulta para a fundação técnica do projeto.

## Problema e escopo

O `sentinel-auth-api` é um projeto de portfólio/GitHub cujo objetivo é demonstrar domínio técnico de Spring Security e das práticas de engenharia associadas a uma API de autenticação — não é um produto real, nem tem usuários reais além do próprio autor, em contexto de estudo.

- É consumido diretamente por um **frontend web** e por um **app mobile**. Não é um serviço "auth-as-a-service" para outros backends consumirem.
- Projeto greenfield — não substitui nenhum sistema existente.
- Escala esperada é pequena (dezenas de usuários, uso pessoal/de estudo), mas o padrão de qualidade almejado é **"production-inspired"** (termo já usado na descrição do projeto): as práticas de engenharia devem ser de nível produção mesmo com escala pequena, já que o objetivo é demonstrar competência técnica, não apenas funcionar.
- Nenhuma restrição não-negociável (compliance, SLA, orçamento de infra, etc.) foi definida até o momento. Esse ponto foi deixado em aberto deliberadamente e pode reaparecer como uma decisão futura, caso surja.

## Estilo arquitetural

- **Camadas simples**: `Controller → Service → Repository`. Decisão consciente de **não** adotar Hexagonal/Clean Architecture — seria complexidade desnecessária frente ao escopo do projeto (KISS/YAGNI).
- **Organização de pacotes por feature/domínio**, não por camada técnica. Exemplo de estrutura:
  ```
  auth/
    controller, service, repository próprios do domínio de autenticação
  user/
    controller, service, repository próprios do domínio de usuário
  rbac/
    controller, service, repository próprios do domínio de papéis
  ```
  Cada pacote de domínio contém suas próprias camadas internas, em vez de um pacote `controllers/`, `services/`, `repositories/` global cortando por toda a aplicação.
- **Autenticação stateless via JWT** — sem sessão em servidor.
- **Sem camada de cache (Redis)** na v0.1.0 — apenas PostgreSQL. Cache é uma possibilidade de versão futura, ainda não decidida.
- **OpenAPI/Swagger** confirmado desde a v0.1.0 — documentação de API é parte do valor de portfólio do projeto.
- **Observabilidade**: Spring Boot Actuator habilitado desde a v0.1.0. Logs simples (não estruturados ainda), mas devem ser claros e úteis. Logs estruturados (ex.: JSON) ficam para uma versão futura.

## Stack técnica

| Categoria | Escolha | Motivação |
|---|---|---|
| Linguagem | Java 25 (LTS) | Versão LTS mais recente disponível |
| Framework | Spring Boot 4.1.x | Núcleo do projeto — foco em demonstrar Spring Security |
| Build tool | Maven | Maior adoção de mercado e menor curva de aprendizado frente ao Gradle |
| Migrations | Flyway | Mais simples que Liquibase para o escopo do projeto |
| Hashing de senha | Argon2id | Algoritmo moderno recomendado para hashing de senha |
| JWT | `jjwt` (`io.jsonwebtoken`) | Biblioteca JWT usada desde a v0.1.0 |
| Testes de integração | Testcontainers (Postgres real) | Já na v0.1.0 — evita mocks de banco em testes de integração |
| Containerização | Docker (`Dockerfile`) + `docker-compose` | Empacota a aplicação e orquestra aplicação + Postgres com um único comando localmente |
| CI | GitHub Actions | Build e testes a cada push/PR |

## Modelo de domínio

### `User`

| Campo | Observação |
|---|---|
| `id` | Identificador |
| `email` | Identificador de login |
| senha (hash) | Via Argon2id — nunca em texto plano |
| `enabled` | Habilita/desabilita a conta |
| `locked` | Bloqueio de conta |
| `emailVerified` | Incluído desde já mesmo sem a feature de verificação de e-mail implementada ainda — custo baixo de adicionar o campo agora, evita uma migração de schema futura |
| `createdAt` | Timestamp de criação |
| `roles` | Relação com `Role` (ver abaixo) |

### `Role`

Tabela própria, em relação N:N com `User`. Sem entidade de `Permission` dinâmica — ver [ADR-0001](adr/0001-lean-rbac-modeling.md) para o raciocínio completo e os trade-offs aceitos.

## Fluxo de autenticação e ciclo de vida de tokens

### Endpoints

Todos sob o prefixo de versionamento `/api/v1` (ver [ADR-0002](adr/0002-uri-based-api-versioning.md)):

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### Tempo de vida dos tokens

- **Access token**: 15 minutos.
- **Refresh token**: 7 dias, com **rotação a cada uso** — cada refresh invalida o token anterior. Decisão explícita: combina simplicidade e boas práticas de segurança, sem custo relevante de implementação adicional.

### Persistência e revogação

O refresh token é **persistido em tabela no PostgreSQL** (não há Redis na v0.1.0), o que permite revogação real no `logout` — ao contrário de um esquema puramente stateless, onde revogar um refresh token antes do seu vencimento natural não seria possível.

### Armazenamento por canal do cliente

A API em si é agnóstica de canal, mas os clientes tratam os tokens de forma diferente conforme a plataforma:

- **Web**: refresh token em cookie `httpOnly`, `Secure`, `SameSite`; access token mantido apenas em memória no cliente (nunca persistido em `localStorage`/`sessionStorage`).
- **Mobile nativo**: armazenamento seguro do sistema operacional — Keychain no iOS, Keystore no Android.

## Padrões de API

- **Versionamento via URI** (`/api/v1`) — ver [ADR-0002](adr/0002-uri-based-api-versioning.md).
- **Erros no formato RFC 9457** ("Problem Details for HTTP APIs", que obsoleta a RFC 7807), via `ProblemDetail` do Spring — ver [ADR-0003](adr/0003-rfc9457-error-format.md).
- **Validação de entrada via Jakarta Bean Validation** em DTOs — ver [ADR-0004](adr/0004-bean-validation-input.md).

## Segurança operacional e segredos

- Segredos nunca são commitados no repositório.
- `.env` local usado apenas em ambiente de desenvolvimento.
- Variáveis de ambiente usadas para configuração do Docker.
- Secrets do GitHub Actions usados para CI/CD.
- Rate limiting está deliberadamente fora do escopo da v0.1.0 — ver [ADR-0005](adr/0005-defer-rate-limiting.md).

## Escopo de testes da v0.1.0

- **Testes de integração** com Testcontainers, cobrindo repositories e o fluxo completo de autenticação via controllers.
- **Testes unitários** de services com Mockito.
- Metas de cobertura e testes de performance/carga **não** fazem parte do escopo da v0.1.0 — ficam para a fase `v0.5.0 — Quality & Security` do roadmap.

## Referências

- [ADR-0001 — Modelagem enxuta de RBAC](adr/0001-lean-rbac-modeling.md)
- [ADR-0002 — Versionamento de API via URI desde o início](adr/0002-uri-based-api-versioning.md)
- [ADR-0003 — Formato de erro padronizado via RFC 9457](adr/0003-rfc9457-error-format.md)
- [ADR-0004 — Validação de entrada via Bean Validation](adr/0004-bean-validation-input.md)
- [ADR-0005 — Rate limiting adiado deliberadamente para a fase de Quality](adr/0005-defer-rate-limiting.md)
