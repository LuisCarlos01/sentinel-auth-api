# Gatling + Maven

## Responsabilidade de cada tecnologia

- **Maven**: dono do ciclo de vida do build (`mvn clean verify`, já o comando padrão de build completo do projeto — ver `CLAUDE.md`, seção "Build/test"). É o Maven, via `gatling-maven-plugin`, que dispara a compilação e execução das simulações Gatling como parte (ou como um goal adicional) desse mesmo pipeline, em vez de depender de uma CLI/runtime externo ao ecossistema Java.
- **Gatling**: dono da execução da simulação de carga em si — gera requisições HTTP concorrentes conforme o cenário definido em Java, mede latência/throughput/taxa de erro, e produz o relatório HTML.

## Fluxo entre elas

1. `AuthFlowSimulation` fica em `src/test/gatling/java`, separado de `src/test/java` — o `gatling-maven-plugin` (série `4.x`) não tem mais um parâmetro de "pasta de simulações"; ele roda sobre o classpath de teste já compilado (fase `test-compile`), então a separação é feita registrando `src/test/gatling/java` como diretório de teste adicional via `build-helper-maven-plugin` (goal `add-test-source`), só dentro do profile `load-test`.
2. `gatling-maven-plugin` é declarado no profile `load-test` com uma `<execution>` do goal `test` (sem `<phase>` explícita — usa o `defaultPhase` do próprio plugin, `integration-test`, disparado por `mvn verify -P load-test`). Sem o profile ativo, o plugin nem entra no build.
3. `AuthFlowSimulation` exercita o fluxo real de autenticação do projeto — `register` → `login` → `refresh` → `logout` — contra a aplicação subida via `docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up` (não Testcontainers).
4. Gatling produz um relatório HTML em `target/gatling/<nome-da-simulação>-<timestamp>/`, com métricas de latência (percentis), throughput e taxa de erro por request nomeado (`register`, `login`, `refresh`, `logout`).

## Configuração necessária

- Dependência `io.gatling.highcharts:gatling-charts-highcharts` `3.15.1` (escopo `test`) — necessária para o plugin gerar os relatórios HTML com gráficos, e fonte do Gatling core em tempo de execução (transitiva).
- Plugin `io.gatling:gatling-maven-plugin` `4.21.10` declarado em `<build><plugins>` — versão independente da do `gatling-charts-highcharts` (ver [`gatling.md`](../technologies/gatling.md)).
- Decisão tomada na implementação (ticket #21): profile Maven dedicado `load-test`, sem `<executions>` no build padrão — `mvn clean verify` sem `-P load-test` nunca dispara a simulação. Disparo via `mvn verify -P load-test` (ou `mvn gatling:test -P load-test`).
- `build-helper-maven-plugin` (`add-test-source`, dentro do mesmo profile) registra `src/test/gatling/java` como diretório de teste adicional — o `gatling-maven-plugin` (série `4.x`) roda sobre as classes já compiladas de teste (não há mais um parâmetro de "pasta de simulações" no plugin), por isso a separação de `src/test/java` exige esse passo extra.
- Aplicação alvo rodando e acessível na URL configurada na simulação (`baseUrl`) — Gatling não sobe a aplicação sozinho; isso precisa estar orquestrado (Docker Compose local, ou um ambiente de CI dedicado, a definir).

## Cuidados e anti-patterns específicos dessa combinação

- **Não ligar o goal `gatling:test` incondicionalmente à fase `verify` do build principal** sem uma decisão consciente — isso faria todo `./mvnw clean verify` (rodado a cada push/PR pelo CI, `CLAUDE.md`) executar teste de carga, aumentando significativamente o tempo de CI para algo que tipicamente roda com menos frequência que testes funcionais.
- **Rodar a simulação de carga do fluxo de login contra o rate limiting Bucket4j sem calibrar dados de teste** — ver [`bucket4j-spring-boot.md`](bucket4j-spring-boot.md). O e-mail varia por usuário virtual (feeder), mas o **IP não varia**: toda a simulação sai da mesma máquina, então o bucket por IP do `LoginRateLimitFilter` bateria o limite de produção (5/min) bem antes de medir a performance real do fluxo. Resolvido via `docker-compose.loadtest.yml` (overlay que eleva `SENTINEL_RATE_LIMIT_LOGIN_CAPACITY` só para o teste de carga), não alterando o comportamento de produção — o comportamento do rate limiter em si já é validado pelos testes de integração do ticket #01.
- **Encadear corretamente os dados entre os passos da simulação** (token do `login` usado no `refresh`/`logout`) via `.check(...saveAs(...))` e variáveis de sessão do Gatling — sem isso, os passos subsequentes falham por token ausente/inválido e a simulação não reflete o uso real do fluxo.
- **Relatórios em `target/gatling/` não devem ser commitados** — mesma regra geral de artefatos de build (`target/` já ignorado).
- **Ambiente da simulação de carga não deve ser o mesmo Postgres usado pelos testes de integração via Testcontainers** — Testcontainers sobe/derruba containers por execução de teste; uma simulação de carga precisa de um ambiente estável e persistente durante toda a execução (ex.: `docker-compose` local ou uma instância dedicada), não o ciclo de vida efêmero do Testcontainers usado pelos testes JUnit.
