# Índice de referência técnica — sentinel-auth-api

Ponto de entrada para a base de referência técnica sincronizada em `docs/technologies/` e `docs/integrations/`. Este índice não duplica o conteúdo dos documentos — apenas aponta para eles. Para as decisões de arquitetura do projeto, ver [`docs/architecture.md`](architecture.md) e os [ADRs](adr/README.md).

> **Estado do projeto**: Phase 0 (arquitetura documentada) concluída; Phase 1 (bootstrap Spring Boot com Maven) ainda não rodou. Não existe `pom.xml` nem código-fonte no repositório. Todos os documentos abaixo foram validados contra a documentação oficial de cada tecnologia (via Context7), **não contra um manifesto de dependências real** — cada documento registra essa pendência explicitamente e deve ser revisitado quando a Phase 1 gerar o projeto Maven.

## Tecnologias

| Documento | Cobre |
|---|---|
| [`spring-boot.md`](technologies/spring-boot.md) | Framework núcleo — versão, organização por feature, camadas, requisitos de Java |
| [`argon2id.md`](technologies/argon2id.md) | Hashing de senha via `Argon2PasswordEncoder` (Spring Security Crypto) |
| [`jjwt.md`](technologies/jjwt.md) | Biblioteca JWT (`io.jsonwebtoken`) — emissão e validação de token |
| [`maven.md`](technologies/maven.md) | Build tool — parent POM, gestão de dependências, empacotamento |
| [`flyway.md`](technologies/flyway.md) | Migração de schema — convenção de nomes, módulo Postgres |

## Integrações

| Documento | Combinação |
|---|---|
| [`argon2id-spring-boot.md`](integrations/argon2id-spring-boot.md) | Bean `PasswordEncoder` e fluxo `register`/`login` |
| [`jjwt-spring-boot.md`](integrations/jjwt-spring-boot.md) | Filtro de segurança customizado + emissão/validação de token |
| [`flyway-spring-boot.md`](integrations/flyway-spring-boot.md) | Autoconfiguração e ordem de execução das migrações no startup |
| [`maven-spring-boot.md`](integrations/maven-spring-boot.md) | Parent POM, BOM de dependências e empacotamento em JAR executável |

## Pendências conhecidas (não cobertas ainda)

Tecnologias declaradas na stack ([`docs/architecture.md`](architecture.md#stack-técnica)) que **não** têm documento próprio nesta sincronização, por decisão explícita de escopo/priorização:

- **Testcontainers** — testes de integração com Postgres real.
- **Docker** (`Dockerfile` + `docker-compose`) — containerização.
- **GitHub Actions** — CI.

Além disso, **PostgreSQL** em si (o banco referenciado em `docs/architecture.md` para persistência de usuários/roles/refresh tokens) não é uma linha própria da tabela "Stack técnica" e não teve documento dedicado nesta rodada.

Essas pendências devem ser cobertas em uma próxima sincronização.
