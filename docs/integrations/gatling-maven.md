# Gatling + Maven

## Responsabilidade de cada tecnologia

- **Maven**: dono do ciclo de vida do build (`mvn clean verify`, já o comando padrão de build completo do projeto — ver `CLAUDE.md`, seção "Build/test"). É o Maven, via `gatling-maven-plugin`, que dispara a compilação e execução das simulações Gatling como parte (ou como um goal adicional) desse mesmo pipeline, em vez de depender de uma CLI/runtime externo ao ecossistema Java.
- **Gatling**: dono da execução da simulação de carga em si — gera requisições HTTP concorrentes conforme o cenário definido em Java, mede latência/throughput/taxa de erro, e produz o relatório HTML.

## Fluxo entre elas

1. As classes de simulação (ex.: `AuthFlowSimulation`) ficam em um diretório de fontes de teste separado — convenção do `gatling-maven-plugin` é `src/test/java` (mesmo diretório dos testes JUnit) por padrão, mas o plugin também suporta configurar um diretório dedicado (`configFolder`/`resultsFolder`/`simulationsFolder`) via `<configuration>` — a escolha exata entre reaproveitar `src/test/java` ou isolar em algo como `src/test/gatling` (convenção comum na comunidade, ainda que não seja o default do plugin) é decisão de implementação da v0.5.0, mas deve favorecer separação clara dos testes JUnit/Mockito/Testcontainers já existentes, já que testes de carga têm um ciclo de execução muito mais longo.
2. `gatling-maven-plugin` é declarado no `pom.xml` com o goal `test` (o goal que efetivamente executa simulações). Pode ser disparado manualmente via `mvn gatling:test`, ou ligado a uma fase do ciclo de vida padrão (ex.: `verify`) via `<executions>`, dependendo de quão automática a execução deve ser em relação ao restante do pipeline de `mvn clean verify` já usado no CI.
3. A simulação `AuthFlowSimulation` (ou equivalente) exercita o fluxo real de autenticação do projeto — `register` → `login` → `refresh` → `logout` — contra uma instância da aplicação já em execução (local, ou subida como parte do próprio build via `spring-boot:start`/Testcontainers/Docker Compose, a definir na implementação).
4. Gatling produz um relatório HTML em `target/gatling/<nome-da-simulação>-<timestamp>/`, com métricas de latência (percentis), throughput e taxa de erro por request nomeado (`register`, `login`, `refresh`, `logout`).

## Configuração necessária

- Dependência `io.gatling.highcharts:gatling-charts-highcharts` (escopo `test`) — necessária para o plugin gerar os relatórios HTML com gráficos.
- Plugin `io.gatling:gatling-maven-plugin` declarado em `<build><plugins>` — versão a confirmar/pinar na v0.5.0 (ver [`gatling.md`](../technologies/gatling.md), pendência de versão).
- Decisão (a tomar na implementação) sobre qual fase do ciclo de vida Maven dispara a simulação: rodar sempre em `mvn clean verify` (mesmo pipeline do CI, `CLAUDE.md`) tornaria todo push mais lento; um profile Maven dedicado (`mvn verify -P load-test`) ou execução manual via `mvn gatling:test` evita esse custo no fluxo padrão — este documento não decide isso, apenas registra o trade-off.
- Aplicação alvo rodando e acessível na URL configurada na simulação (`baseUrl`) — Gatling não sobe a aplicação sozinho; isso precisa estar orquestrado (Docker Compose local, ou um ambiente de CI dedicado, a definir).

## Cuidados e anti-patterns específicos dessa combinação

- **Não ligar o goal `gatling:test` incondicionalmente à fase `verify` do build principal** sem uma decisão consciente — isso faria todo `./mvnw clean verify` (rodado a cada push/PR pelo CI, `CLAUDE.md`) executar teste de carga, aumentando significativamente o tempo de CI para algo que tipicamente roda com menos frequência que testes funcionais.
- **Rodar a simulação de carga do fluxo de login contra o rate limiting Bucket4j sem calibrar dados de teste** (IP/e-mail variados por usuário virtual da simulação) — ver [`bucket4j-spring-boot.md`](bucket4j-spring-boot.md); sem isso, a simulação mede o comportamento do rate limiter, não a performance real do fluxo de autenticação.
- **Encadear corretamente os dados entre os passos da simulação** (token do `login` usado no `refresh`/`logout`) via `.check(...saveAs(...))` e variáveis de sessão do Gatling — sem isso, os passos subsequentes falham por token ausente/inválido e a simulação não reflete o uso real do fluxo.
- **Relatórios em `target/gatling/` não devem ser commitados** — mesma regra geral de artefatos de build (`target/` já ignorado).
- **Ambiente da simulação de carga não deve ser o mesmo Postgres usado pelos testes de integração via Testcontainers** — Testcontainers sobe/derruba containers por execução de teste; uma simulação de carga precisa de um ambiente estável e persistente durante toda a execução (ex.: `docker-compose` local ou uma instância dedicada), não o ciclo de vida efêmero do Testcontainers usado pelos testes JUnit.
