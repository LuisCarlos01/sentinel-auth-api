# Flyway

## Versão e propósito

**Flyway** é a ferramenta de migração de banco declarada em [`docs/architecture.md`](../architecture.md#stack-técnica) ("mais simples que Liquibase para o escopo do projeto"). Nem `docs/architecture.md` nem nenhum ADR fixam uma versão específica.

Ponto tecnicamente relevante e fácil de esquecer: **desde a divisão modular do Flyway (Flyway 10+), o suporte a PostgreSQL não vem mais embutido em `flyway-core`** — é necessário declarar também o módulo `org.flywaydb:flyway-database-postgresql` como dependência separada (descoberto em runtime via `ServiceLoader`, não em compile-time). Isso é facilmente esquecido por quem já usou Flyway antes dessa divisão modular. Como o projeto usa PostgreSQL (via Testcontainers em teste e via `docker-compose` em desenvolvimento), essa dependência extra é necessária.

> **Pendência explícita**: sem `pom.xml`, a versão real do Flyway usada pelo projeto ainda não está fixada.

## Quando usar

Toda evolução de schema do banco: criação das tabelas `users`, `roles` (N:N via tabela de junção — [ADR-0001](../adr/0001-lean-rbac-modeling.md)), e da tabela de refresh tokens persistidos (`docs/architecture.md`, seção "Persistência e revogação").

## Boas práticas como aplicadas neste projeto

- **Convenção de nomes**: migrações versionadas seguem `V{versão}__{descrição}.sql` (prefixo `V`, separador duplo underscore `__` por padrão), em `src/main/resources/db/migration` (local default reconhecido pela autoconfiguração do Spring Boot para Flyway).
- **Dependência dupla para PostgreSQL**: `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` — a segunda é obrigatória para Postgres desde a modularização do Flyway, mesmo que não apareça como dependência transitiva óbvia.
- **Flyway roda automaticamente no startup** via autoconfiguração do Spring Boot, antes de qualquer validação de schema pela camada de acesso a dados — isso vale tanto localmente/CI (Postgres real via `docker-compose`) quanto nos testes de integração com Testcontainers (Postgres real também), consistente com a decisão de "evitar mocks de banco em testes de integração" (`docs/architecture.md`).
- **Se a camada de persistência for Spring Data JPA/Hibernate** (opção mais comum no ecossistema Spring Boot para o padrão `Repository`, mas **não é uma decisão explicitamente registrada** em `docs/architecture.md` — apenas a premissa mais provável): configurar `spring.jpa.hibernate.ddl-auto=validate` (ou `none`), nunca `update`/`create`. O Flyway deve ser a única fonte de verdade do schema; o Hibernate apenas valida (ou nem isso) contra o schema já migrado.
- **Uma migração por unidade lógica de mudança** (`V1__create_users_table.sql`, `V2__create_roles_table.sql`, `V3__create_refresh_tokens_table.sql`, etc.) — a granularidade exata é decisão de implementação da fase `v0.2.0 — User Persistence` do roadmap, não antecipada aqui.

## Anti-patterns

- **Editar ou apagar uma migração já aplicada** — Flyway guarda checksum de cada migração aplicada; alterar o conteúdo de um arquivo já rodado quebra `flyway validate` em qualquer ambiente onde a migração antiga já rodou (inclusive CI). Qualquer correção de schema é uma **nova** migração.
- **Deixar Hibernate com `ddl-auto=update`/`create` convivendo com Flyway** — duas fontes de verdade competindo pelo mesmo schema é o conflito clássico dessa combinação.
- **Esquecer `flyway-database-postgresql`** e assumir que `flyway-core` sozinho basta para Postgres (válido em versões antigas do Flyway, não nas atuais).
- **Colocar dado de ambiente específico (ex.: usuário de teste, seed local) em migração versionada** — se necessário, usar migração repetível (`R__descricao.sql`) para dado de referência idempotente, nunca versionada para dado que muda por ambiente.

## Exemplo mínimo

```sql
-- V1__create_users_table.sql (referência — não existe ainda no repo)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    locked BOOLEAN NOT NULL DEFAULT false,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Dependências Maven necessárias (a confirmar/pinar na Phase 1):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

## Integrações relacionadas

- [`flyway-spring-boot.md`](../integrations/flyway-spring-boot.md) — autoconfiguração e ordem de execução no startup.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/flyway/flyway`.
- **Versão consultada**: documentação de referência geral (naming convention, configuração) e release notes até Flyway 13.4.0 (2026-08-26), usadas apenas para confirmar a separação do módulo `flyway-database-postgresql` — não fixa uma versão específica recomendada para o projeto.
- **Data da consulta**: 2026-09-02.
