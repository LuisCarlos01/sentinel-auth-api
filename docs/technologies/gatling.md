# Gatling

## Versão e propósito

**Gatling** é a ferramenta de teste de carga escolhida para a fase `v0.5.0 — Quality & Security` (issue-mãe #6), para o fluxo completo de autenticação (`register`/`login`/`refresh`/`logout`). Decisão tomada em sessão de grilling com o dono do projeto (ainda **não** registrada em ADR nem em `docs/architecture.md`): Gatling foi escolhido especificamente por **integrar nativamente ao ciclo de vida do Maven** (`mvn verify`, via `gatling-maven-plugin`), evitando depender de uma ferramenta externa ao ecossistema Java já estabelecido no projeto (Java 25, Maven, sem Node/JS em nenhuma outra parte da stack) — ao contrário de alternativas como k6 (JS) ou Artillery (Node).

> **Pendência resolvida (implementação do ticket #21)**: o Context7 não retornara um número de versão único e confiável (apenas `3.9.5`, de um guia de migração histórico). Versões confirmadas diretamente no projeto de demonstração oficial do Gatling
> ([`gatling/gatling-maven-plugin-demo-java`](https://github.com/gatling/gatling-maven-plugin-demo-java/blob/main/pom.xml), mantido por dependabot) e cruzadas com o Maven Central: `gatling-maven-plugin` **4.21.10** e `gatling-charts-highcharts` **3.15.1** — **não são a mesma versão**: o plugin, a partir da série `4.x`, tem seu próprio esquema de versionamento, independente da versão do Gatling core usada em tempo de execução (que vem transitivamente de `gatling-charts-highcharts`, declarada como dependência de teste do projeto, não do plugin).

## Quando usar

Decisão já tomada (não um ponto em aberto): testes de carga do **fluxo completo de autenticação** —

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

— executados via `mvn verify` (ou um profile/goal Maven dedicado, a definir na implementação), não como uma ferramenta externa rodada manualmente fora do build. Isso mantém os testes de carga no mesmo pipeline de qualidade do restante do projeto (mesmo racional do CI já existente, que roda `./mvnw -B clean verify`).

## Boas práticas como aplicadas neste projeto

> Esta seção é baseada em decisão documentada (grilling session), não em código observado — ainda não há simulação Gatling no projeto para confirmar o padrão real de uso.

- **Simulações em Java**, não Scala — o projeto é 100% Java (Spring Boot, Java 25); usar o DSL Java do Gatling (`io.gatling.javaapi.core.*`, `io.gatling.javaapi.http.*`) evita introduzir uma segunda linguagem de JVM no projeto só para os testes de carga.
- **Diretório convencional `src/test/java` (ou `src/gatling/java`, a confirmar na config do plugin) para as classes de simulação**, separado dos testes JUnit/Mockito/Testcontainers já existentes (`docs/architecture.md`, seção "Escopo de testes") — carga não é a mesma categoria de teste que unitário/integração e não deve rodar no mesmo goal `mvn test` por padrão, para não pagar o custo de um teste de carga a cada `./mvnw test` local.
- **Simular o fluxo real e ordenado do ciclo de vida do token**: `register` → `login` → chamada autenticada (opcional) → `refresh` → `logout`, encadeando os dados de resposta de um passo (ex.: token emitido no `login`) como entrada do passo seguinte (`refresh`), via `.exec(http(...).check(jsonPath("$.accessToken").saveAs("accessToken")))` e reuso da sessão Gatling — não simulações isoladas e desconexas de cada endpoint.
- **Rodar a suíte de carga separada do `login` limitado por Bucket4j** (ver [`bucket4j.md`](bucket4j.md)) ou usar dados de usuário/IP distintos por requisição simulada — senão o rate limiting da v0.5.0 derruba a simulação de carga do próprio `login` antes de medir performance real, confundindo "limite de taxa atingido" com "sistema sob carga".
- **Relatório HTML gerado em `target/gatling/`** (local default do plugin) — não commitar esse diretório (já deveria estar coberto por `.gitignore` de `target/`).

## Anti-patterns

- Rodar Gatling como parte do goal `mvn test` (que roda em todo `./mvnw clean verify` do CI a cada push) sem isolar a execução — testes de carga são mais lentos e não devem bloquear todo push/PR da mesma forma que testes unitários/integração; a integração ao Maven deve ser deliberada sobre em qual fase/profile a simulação roda.
- Escrever simulações que ignoram o rate limiting de `login` (Bucket4j) e reportam "degradação de performance" que na verdade é o 429 esperado — os testes de carga precisam ser desenhados cientes do rate limiting já decidido para esta mesma fase.
- Misturar simulação de carga com asserção de corretude funcional fina (isso é papel dos testes de integração já existentes, Testcontainers) — Gatling mede performance/comportamento sob carga, não deve duplicar a responsabilidade dos testes funcionais.
- Hardcoded de URLs/portas/segredos na simulação em vez de configuração externa (variável de ambiente/`application-test.yml` equivalente) — mesma preocupação de segredos já registrada em `docs/architecture.md`.

## Exemplo mínimo

```java
// Referência — não existe ainda no repo
public class AuthFlowSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    ScenarioBuilder authFlow = scenario("Auth flow")
        .exec(http("register")
            .post("/api/v1/auth/register")
            .body(StringBody("{...}")))
        .exec(http("login")
            .post("/api/v1/auth/login")
            .body(StringBody("{...}"))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
            .check(jsonPath("$.refreshToken").saveAs("refreshToken")))
        .exec(http("refresh")
            .post("/api/v1/auth/refresh")
            .body(StringBody("{\"refreshToken\": \"#{refreshToken}\"}")))
        .exec(http("logout")
            .post("/api/v1/auth/logout")
            .header("Authorization", "Bearer #{accessToken}"));

    {
        setUp(authFlow.injectOpen(rampUsers(50).during(30)))
            .protocols(httpProtocol);
    }
}
```

Dependência e plugin Maven, refletidos no `pom.xml` real (profile `load-test`, ticket #21):

```xml
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>${gatling.version}</version> <!-- 3.15.1 -->
    <scope>test</scope>
</dependency>
```

```xml
<plugin>
    <groupId>io.gatling</groupId>
    <artifactId>gatling-maven-plugin</artifactId>
    <version>${gatling-maven-plugin.version}</version> <!-- 4.21.10 -->
</plugin>
```

## Integrações relacionadas

- [`gatling-maven.md`](../integrations/gatling-maven.md) — binding ao ciclo de vida do Maven (`mvn verify`), convenção de diretório de simulações, e relação com o fluxo de auth real do projeto.

## Proveniência

- **Provedor original (referência geral)**: Context7, biblioteca `/gatling/gatling.io-doc` — não retornou uma versão estável atual confiável (a única versão numérica, `3.9.5`, veio de um guia de migração histórico), por isso não foi usado para pinar a versão.
- **Versões pinadas (`gatling-maven-plugin` 4.21.10, `gatling.version`/`gatling-charts-highcharts` 3.15.1)**: confirmadas no projeto de demonstração oficial do Gatling ([`gatling/gatling-maven-plugin-demo-java`](https://github.com/gatling/gatling-maven-plugin-demo-java/blob/main/pom.xml)) e cruzadas com `maven-metadata.xml` do Maven Central.
- **Data da confirmação**: 2026-09-07 (implementação do ticket #21).
