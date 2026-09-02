# Spring Boot + Flyway

## Responsabilidade de cada tecnologia

- **Spring Boot**: detecta o Flyway no classpath e o autoconfigura (`FlywayAutoConfiguration`) — cria o `DataSource`, injeta na instância de `Flyway`, e garante que as migrações rodem **antes** de qualquer bean que dependa do schema estar pronto (inclusive antes de o Hibernate/JPA, se usado, validar entidades contra o schema).
- **Flyway**: dono do versionamento do schema em si — aplica migrações SQL versionadas em ordem, mantém a tabela de histórico (`flyway_schema_history`), e falha o startup da aplicação se detectar checksum divergente de uma migração já aplicada.

## Fluxo entre elas

1. No startup do contexto Spring, a autoconfiguração do Flyway localiza os arquivos em `src/main/resources/db/migration` (local default) e os aplica, em ordem de versão, contra o `DataSource` configurado.
2. Isso acontece **igualmente em desenvolvimento local** (Postgres via `docker-compose`) **e nos testes de integração** (Postgres real via Testcontainers) — não há divergência de mecanismo entre os dois ambientes, o que é coerente com a decisão de "evitar mocks de banco em testes de integração" (`docs/architecture.md`).
3. Só depois de o Flyway concluir a migração é que o restante do contexto Spring (repositories, camada de acesso a dados) fica disponível para receber tráfego/testes.

## Configuração necessária

- Dependências: `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` (a segunda é obrigatória para Postgres desde a modularização do Flyway — ver [`flyway.md`](../technologies/flyway.md)).
- `DataSource` configurado (via `application.yml`/variáveis de ambiente) apontando para o Postgres — a mesma configuração de conexão usada pelo restante da aplicação; o Flyway não usa uma conexão separada.
- Se a persistência usar Hibernate/JPA: `spring.jpa.hibernate.ddl-auto=validate` (ou `none`), para que o Hibernate nunca tente alterar o schema por conta própria, competindo com o Flyway.

## Cuidados e anti-patterns específicos dessa combinação

- **Ordem de dependência do Testcontainers**: nos testes de integração, o container do Postgres precisa estar de pé e o `DataSource` apontando para ele **antes** de o Spring tentar rodar o Flyway — normalmente resolvido com `@ServiceConnection`/`@DynamicPropertySource` (Spring Boot Test + Testcontainers), garantindo que a URL/porta dinâmica do container seja injetada antes da autoconfiguração do Flyway rodar.
- **Migração aplicada com sucesso localmente mas quebrando em CI** costuma ser sintoma de dependência implícita de estado (ex.: assumir uma extensão do Postgres já habilitada, como `pgcrypto` para `gen_random_uuid()`) — qualquer extensão necessária deve ser habilitada por uma migração própria (`CREATE EXTENSION IF NOT EXISTS ...`), não assumida como pré-existente no ambiente.
- **Não deixar checksum de migração já aplicada mudar** entre o que rodou localmente e o que roda no GitHub Actions (CI) — isso normalmente acontece se alguém edita uma migração já commitada em vez de criar uma nova; o Flyway falha o build de forma explícita nesse caso, o que é o comportamento correto (não silenciar/contornar isso, corrigir a causa).
