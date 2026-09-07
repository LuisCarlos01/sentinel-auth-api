# JaCoCo

## Versão e propósito

**JaCoCo** (`jacoco-maven-plugin`) é a ferramenta de medição de cobertura de testes escolhida para a fase `v0.5.0 — Quality & Security` (issue-mãe #6). Decisão tomada em sessão de grilling com o dono do projeto (ainda **não** registrada em ADR nem em `docs/architecture.md`): JaCoCo foi escolhido por ser o **plugin Maven padrão do ecossistema Java** para medição de cobertura via instrumentação de bytecode — mesma lógica de "ferramenta nativa do ecossistema já estabelecido" usada para justificar Gatling (ver [`gatling.md`](gatling.md)).

> **Pendência resolvida (implementação do ticket #20)**: versão confirmada diretamente no changelog oficial (`jacoco.org/jacoco/trunk/doc/changes.html`), não pela sugestão do Context7 (que não retornara um número confiável). `jacoco-maven-plugin` **0.8.15** — suporte oficial ao bytecode do Java 25 desde a 0.8.14, e à 0.8.15 (a mais recente no momento) já suporta oficialmente Java 26.

## Quando usar

Decisão já tomada (não um ponto em aberto): medir cobertura de testes de todo o código de produção do projeto (unitários com Mockito + integração com Testcontainers, `docs/architecture.md` seção "Escopo de testes"), gerando relatório a cada `mvn verify`.

**Nenhuma meta numérica de cobertura foi fixada** em nenhum documento do projeto até agora (nem `docs/architecture.md`, nem nenhum ADR, nem a issue-mãe #6). Este documento **não inventa um número** (ex.: "80% de cobertura de linha") — se/quando o dono do projeto decidir um threshold mínimo, a decisão deve ser registrada no lugar apropriado (ADR ou na própria issue-mãe/roadmap), e então o goal `jacoco:check` pode ser configurado para falhar o build abaixo dele. Até lá, JaCoCo roda apenas em modo de **medição e relatório**, não de **enforcement**.

## Boas práticas como aplicadas neste projeto

> Esta seção é baseada em decisão documentada (grilling session), não em código observado — ainda não há configuração JaCoCo no projeto para confirmar o padrão real de uso.

- **Dois goals mínimos, ligados ao ciclo de vida padrão do Maven**: `prepare-agent` (ligado à fase `test` ou antes, instrumenta o bytecode antes de os testes rodarem) e `report` (ligado à fase `verify` ou `test`, gera o relatório HTML/XML a partir dos dados coletados) — consistente com `docs/architecture.md`/CLAUDE.md, onde `mvn clean verify` já é "o mesmo pipeline do CI" e o comando único usado para build completo.
- **`jacoco:check` fica desligado (sem execução configurada) até que um threshold seja explicitamente decidido** — não configurar um `<rule>`/`<limit>` "provisório" ou "razoável" por conta própria; isso seria inventar uma meta que ninguém decidiu.
- **Relatório cobre testes unitários (Mockito) e de integração (Testcontainers) juntos** — como ambos já rodam na fase `test`/`verify` do mesmo `mvn clean verify` (não há um goal Failsafe separado documentado no projeto; testes de integração já rodam via Surefire no ciclo padrão, conforme `CLAUDE.md`), um único `jacoco.exec` agregado é suficiente; não é necessário configurar `report-aggregate` de múltiplos módulos (o projeto é mono-módulo).
- **Excluir classes que não fazem sentido medir** (ex.: classe principal `@SpringBootApplication` só com `main()`, DTOs simples sem lógica, se aplicável) via `<excludes>` na configuração do plugin — decisão de granularidade fina para a implementação, mas o princípio de não perseguir 100% artificial em código sem lógica de negócio é válido desde já.
- **Relatório gerado em `target/site/jacoco/`** (local default do plugin) — não commitar esse diretório (já coberto por `.gitignore` de `target/`).

## Anti-patterns

- **Fixar um número de cobertura mínima "por padrão do mercado" (ex.: 80%) sem essa decisão ter sido tomada pelo dono do projeto** — nenhuma meta foi decidida até agora; inventar uma é o erro mais fácil de cometer ao configurar `jacoco:check` "só porque é boa prática genérica".
- **Perseguir cobertura alta em código sem lógica** (getters/setters, DTOs, classe `main()`) só para o número subir — cobertura é um proxy de qualidade, não o objetivo em si.
- **Deixar `prepare-agent` fora do ciclo de vida do build e rodar o agente manualmente** — perde a integração automática com `mvn verify` que foi justamente o motivo da escolha do JaCoCo (mesmo racional do Gatling).
- **Configurar `jacoco:check` para falhar o build com um threshold e depois abaixar esse threshold silenciosamente quando a cobertura cai**, em vez de investigar a causa — se um threshold vier a ser adotado no futuro, ele deve ser tratado como um contrato real, não um número decorativo.

## Exemplo mínimo

```xml
<!-- Refletido no pom.xml real (ticket #20) — versão 0.8.15, property jacoco.version. -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <!-- jacoco:check deliberadamente omitido: nenhum threshold de
             cobertura foi decidido pelo dono do projeto até o momento. -->
    </executions>
</plugin>
```

## Integrações relacionadas

- [`jacoco-maven.md`](../integrations/jacoco-maven.md) — binding do plugin ao `mvn verify`, geração de relatório, e a nota explícita sobre ausência de meta numérica de cobertura.

## Proveniência

- **Provedor original (referência geral)**: Context7, biblioteca `/jacoco/jacoco` — não retornou uma versão estável atual confiável (só changelog histórico até Java 15/16/17), por isso não foi usado para pinar a versão.
- **Versão pinada (`0.8.15`)**: confirmada diretamente em `maven-metadata.xml` do Maven Central (`repo1.maven.org/maven2/org/jacoco/jacoco-maven-plugin/`) e no changelog oficial (`jacoco.org/jacoco/trunk/doc/changes.html`) — suporte oficial a Java 25 desde a 0.8.14, a 0.8.15 (mais recente) já suporta oficialmente Java 26.
- **Data da confirmação**: 2026-09-07 (implementação do ticket #20).
