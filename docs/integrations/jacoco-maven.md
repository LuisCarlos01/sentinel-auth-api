# JaCoCo + Maven

## Responsabilidade de cada tecnologia

- **Maven**: dono do ciclo de vida do build. É o Maven que dispara os goals do `jacoco-maven-plugin` nas fases corretas (`prepare-agent` antes dos testes rodarem, `report` depois), como parte do mesmo `mvn clean verify` que já é "o mesmo pipeline do CI" (`CLAUDE.md`, seção "Build/test").
- **JaCoCo**: dono da instrumentação de bytecode e da coleta de dados de execução (`jacoco.exec`) durante a execução dos testes (Surefire, e também Failsafe se o projeto vier a separá-lo — hoje testes de integração já rodam no ciclo padrão via Surefire, conforme `CLAUDE.md`), e da geração do relatório de cobertura a partir desses dados.

## Fluxo entre elas

1. `mvn clean verify` inicia. O goal `jacoco:prepare-agent` do plugin roda cedo no ciclo de vida (tipicamente ligado à fase `initialize` ou implicitamente antes de `test`, conforme a documentação oficial do plugin) e configura um argumento de JVM (`argLine`) que ativa o agente Java de instrumentação do JaCoCo.
2. O Maven Surefire Plugin executa os testes unitários (Mockito) e de integração (Testcontainers, Postgres real) normalmente — o agente JaCoCo, já ativo via `argLine`, instrumenta as classes em tempo de execução e grava dados de cobertura incrementalmente em `target/jacoco.exec`.
3. Após os testes, o goal `jacoco:report` (ligado à fase `test` ou `verify`, a decidir na implementação) lê `target/jacoco.exec` e gera o relatório em `target/site/jacoco/` (HTML navegável por pacote/classe/linha, mais XML/CSV se configurado).
4. **Nenhum goal `jacoco:check` está configurado nesta fase** — não há falha de build por cobertura insuficiente, porque nenhum threshold numérico foi decidido pelo dono do projeto até o momento (ver [`jacoco.md`](../technologies/jacoco.md)). JaCoCo participa do `mvn verify` apenas em modo de medição/relatório.

## Configuração necessária

- Plugin `org.jacoco:jacoco-maven-plugin` declarado em `<build><plugins>`, versão `0.8.15` (confirmada no changelog oficial, compatível com Java 25 — ver [`jacoco.md`](../technologies/jacoco.md)).
- Duas execuções mínimas: `prepare-agent` (sem fase explícita necessária — o goal já se liga corretamente ao ciclo de vida por convenção do plugin) e `report` (fase `verify`, para que o relatório reflita o `mvn clean verify` completo, mesmo comando já usado para build completo no projeto).
- Nenhuma configuração de `<rules>`/`<limits>` para `jacoco:check` — deliberadamente omitida (ver acima).

## Cuidados e anti-patterns específicos dessa combinação

- **Não presumir uma meta de cobertura e configurar `jacoco:check` com um número "razoável" por conta própria** — este é o cuidado mais importante desta combinação especificamente neste projeto: nenhum documento (arquitetura, ADR, issue-mãe #6) fixou um threshold. Adicionar um sem essa decisão explícita do dono do projeto inventaria um requisito não pedido.
- **Verificar compatibilidade da versão do JaCoCo com Java 25 antes de pinar** — instrumentação de bytecode é sensível à versão do JDK-alvo; uma versão do JaCoCo defasada em relação ao Java 25 pode falhar silenciosamente ao instrumentar classes compiladas com esse target, ou simplesmente não suportar o bytecode gerado. Esse é o mesmo tipo de cuidado já registrado para outras dependências não gerenciadas pelo BOM do Spring Boot (ex.: BouncyCastle no `pom.xml` atual).
- **Relatório em `target/site/jacoco/` não deve ser commitado** — artefato de build, mesma regra de `target/` já ignorado no repositório.
- **Testes de integração via Testcontainers precisam do Docker disponível** (`CLAUDE.md`) para rodar; sem Docker, o `mvn clean verify` já falha antes mesmo de o JaCoCo entrar em cena — isso não é uma limitação do JaCoCo, mas afeta a completude do relatório de cobertura gerado localmente sem Docker rodando (cobertura de repositories/fluxo completo de auth ficaria ausente do relatório).
