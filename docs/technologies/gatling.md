# Gatling

## Versão e propósito

**Gatling** é a ferramenta de teste de carga escolhida para a fase `v0.5.0 — Quality & Security` (issue-mãe #6), para o fluxo completo de autenticação (`register`/`login`/`refresh`/`logout`). Decisão tomada em sessão de grilling com o dono do projeto (ainda **não** registrada em ADR nem em `docs/architecture.md`): Gatling foi escolhido especificamente por **integrar nativamente ao ciclo de vida do Maven** (`mvn verify`, via `gatling-maven-plugin`), evitando depender de uma ferramenta externa ao ecossistema Java já estabelecido no projeto (Java 25, Maven, sem Node/JS em nenhuma outra parte da stack) — ao contrário de alternativas como k6 (JS) ou Artillery (Node).

> **Pendência explícita**: não existe `pom.xml` com Gatling declarado ainda — nenhuma dependência/plugin de teste de carga foi adicionada ao projeto até o momento. A documentação do Context7 para este projeto não retornou um número de versão único e atual de forma confiável (as referências encontradas incluem uma versão de exemplo desatualizada, `3.9.5`, dentro de um guia de migração histórico) — **não fixar uma versão aqui por suposição**. Confirmar a versão estável mais recente de `gatling-charts-highcharts` e `gatling-maven-plugin` (devem ser a mesma versão, os dois artefatos são versionados em conjunto) diretamente em [https://mvnrepository.com/artifact/io.gatling/gatling-maven-plugin](https://mvnrepository.com/artifact/io.gatling/gatling-maven-plugin) ou no changelog oficial quando a implementação da v0.5.0 rodar.

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

Dependência e plugin Maven (versão a confirmar/pinar na v0.5.0 — ver "Pendência explícita" acima):

```xml
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <scope>test</scope>
</dependency>
```

```xml
<plugin>
    <groupId>io.gatling</groupId>
    <artifactId>gatling-maven-plugin</artifactId>
</plugin>
```

## Integrações relacionadas

- [`gatling-maven.md`](../integrations/gatling-maven.md) — binding ao ciclo de vida do Maven (`mvn verify`), convenção de diretório de simulações, e relação com o fluxo de auth real do projeto.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/gatling/gatling.io-doc`.
- **Versão consultada**: documentação de referência geral (setup do plugin Maven, DSL de simulação, diretório de relatório `target/gatling/`) — **não** foi possível confirmar um número de versão estável atual único através das consultas feitas; a única versão numérica retornada (`3.9.5`) veio de um guia de migração histórico e não deve ser tratada como a versão atual recomendada.
- **Data da consulta**: 2026-09-07.
