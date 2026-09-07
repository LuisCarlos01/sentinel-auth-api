# Índice de referência técnica — sentinel-auth-api

Ponto de entrada para a base de referência técnica sincronizada em `docs/technologies/` e `docs/integrations/`. Este índice não duplica o conteúdo dos documentos — apenas aponta para eles. Para as decisões de arquitetura do projeto, ver [`docs/architecture.md`](architecture.md) e os [ADRs](adr/README.md).

> **Estado do projeto**: o `pom.xml` já existe (Spring Boot 4.1.0, Java 25 — projeto passou da Phase 0/1). Os documentos de `spring-boot`, `argon2id`, `jjwt`, `maven` e `flyway` foram validados contra esse `pom.xml` real. Já os documentos de `bucket4j`, `gatling` e `jacoco` (sincronizados para a fase `v0.5.0 — Quality & Security`) **ainda não têm dependência/plugin correspondente no `pom.xml`** — cada um registra essa pendência explicitamente e deve ser revisitado quando a implementação da v0.5.0 adicionar essas dependências.

## Tecnologias

| Documento | Cobre |
|---|---|
| [`spring-boot.md`](technologies/spring-boot.md) | Framework núcleo — versão, organização por feature, camadas, requisitos de Java |
| [`argon2id.md`](technologies/argon2id.md) | Hashing de senha via `Argon2PasswordEncoder` (Spring Security Crypto) |
| [`jjwt.md`](technologies/jjwt.md) | Biblioteca JWT (`io.jsonwebtoken`) — emissão e validação de token |
| [`maven.md`](technologies/maven.md) | Build tool — parent POM, gestão de dependências, empacotamento |
| [`flyway.md`](technologies/flyway.md) | Migração de schema — convenção de nomes, módulo Postgres |
| [`bucket4j.md`](technologies/bucket4j.md) | Rate limiting em memória (token-bucket) — v0.5.0, restrito a `POST /api/v1/auth/login` |
| [`gatling.md`](technologies/gatling.md) | Testes de carga do fluxo completo de autenticação — v0.5.0 |
| [`jacoco.md`](technologies/jacoco.md) | Medição de cobertura de testes — v0.5.0, sem meta numérica fixada ainda |

## Integrações

| Documento | Combinação |
|---|---|
| [`argon2id-spring-boot.md`](integrations/argon2id-spring-boot.md) | Bean `PasswordEncoder` e fluxo `register`/`login` |
| [`jjwt-spring-boot.md`](integrations/jjwt-spring-boot.md) | Filtro de segurança customizado + emissão/validação de token |
| [`flyway-spring-boot.md`](integrations/flyway-spring-boot.md) | Autoconfiguração e ordem de execução das migrações no startup |
| [`maven-spring-boot.md`](integrations/maven-spring-boot.md) | Parent POM, BOM de dependências e empacotamento em JAR executável |
| [`bucket4j-spring-boot.md`](integrations/bucket4j-spring-boot.md) | Filtro de rate limiting no `login` (IP + e-mail) e tradução para RFC 9457 |
| [`gatling-maven.md`](integrations/gatling-maven.md) | Binding ao ciclo de vida do Maven (`mvn verify`) e simulação do fluxo de auth |
| [`jacoco-maven.md`](integrations/jacoco-maven.md) | Binding do plugin ao `mvn verify` e geração de relatório de cobertura |

## Pendências conhecidas (não cobertas ainda)

Tecnologias declaradas na stack ([`docs/architecture.md`](architecture.md#stack-técnica)) que **não** têm documento próprio nesta sincronização, por decisão explícita de escopo/priorização:

- **Testcontainers** — testes de integração com Postgres real.
- **Docker** (`Dockerfile` + `docker-compose`) — containerização.
- **GitHub Actions** — CI.

Além disso, **PostgreSQL** em si (o banco referenciado em `docs/architecture.md` para persistência de usuários/roles/refresh tokens) não é uma linha própria da tabela "Stack técnica" e não teve documento dedicado nesta rodada.

Essas pendências devem ser cobertas em uma próxima sincronização.
