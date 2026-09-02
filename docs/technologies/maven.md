# Maven

## Versão e propósito

**Maven** é o build tool declarado em [`docs/architecture.md`](../architecture.md#stack-técnica) ("maior adoção de mercado e menor curva de aprendizado frente ao Gradle"). Nem `docs/architecture.md` nem nenhum ADR fixam uma versão específica do Maven em si (é a ferramenta de build, versão do binário/wrapper, distinta da versão do Spring Boot). A documentação oficial recomenda **Maven 3.6.3 ou superior** como requisito para uso com Spring Boot 4.x — qualquer instalação atual (ou `maven-wrapper`) atende.

> **Pendência explícita**: sem `pom.xml`, não há como confirmar a versão de Maven/wrapper que a Phase 1 vai efetivamente usar. Este documento cobre convenções, não uma versão pinada.

## Quando usar

Ferramenta de build do projeto inteiro — compilação, gestão de dependências, empacotamento (via `spring-boot-maven-plugin`), execução de testes (`maven-surefire-plugin`/`maven-failsafe-plugin` para os testes de integração com Testcontainers).

## Boas práticas como aplicadas neste projeto

- **Herdar de `spring-boot-starter-parent`** como `<parent>` do `pom.xml` — importa o BOM `spring-boot-dependencies`, o que permite omitir `<version>` em qualquer dependência gerida pelo Spring Boot (todos os `spring-boot-starter-*`), e configura defaults sãos de plugins (compiler, surefire, etc.).
- **Uma única propriedade para a versão do Java**: `<java.version>25</java.version>` nas `<properties>` — o parent do Spring Boot propaga isso para `maven-compiler-plugin` via `maven.compiler.release`, garantindo que a compilação seja checada contra a API exata da baseline (Java 25 LTS), não apenas o nível de linguagem.
- **Não fixar versão manual em dependências geridas pelo BOM** (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa`, etc.) — deixar o BOM decidir, para preservar a combinação de versões testada pelo próprio Spring Boot.
- **Fixar versão explícita para dependências fora do BOM do Spring Boot**, como os artefatos do `jjwt` (`io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` — ver [`jjwt.md`](jjwt.md)), já que o Spring Boot não gerencia essa biblioteca.
- **`spring-boot-maven-plugin`** para repackaging em JAR executável — é o artefato que o `Dockerfile` (multi-stage) vai empacotar na imagem final.
- **Escopo correto por dependência de teste**: Testcontainers com `<scope>test</scope>`, já que são dependências exclusivas dos testes de integração (`docs/architecture.md`, seção "Escopo de testes da v0.1.0").
- **Layout padrão do Maven** (`src/main/java`, `src/main/resources`, `src/test/java`) acomoda diretamente a organização por feature/domínio decidida na arquitetura — pacotes por feature (`auth/`, `user/`, `rbac/`) vivem normalmente dentro de `src/main/java/<groupId-path>/`, sem nenhuma configuração especial de Maven.

## Anti-patterns

- Sobrescrever versões de plugins/dependências já geridas pelo parent `spring-boot-starter-parent` sem necessidade — gera combinações não testadas pelo time do Spring Boot.
- Usar `<maven.compiler.source>`/`<maven.compiler.target>` (estilo antigo) em vez de `<java.version>` (via parent) ou `<maven.compiler.release>` diretamente — `release` valida contra a API pública exata da versão alvo do JDK, os pares `source`/`target` antigos não.
- Múltiplos módulos/profiles Maven sem necessidade real — o projeto é um único módulo, escopo pequeno; não introduzir modularização Maven especulativa.
- Comitar `target/` no controle de versão.

## Exemplo mínimo

Esqueleto ilustrativo de `pom.xml` (referência para a Phase 1 — este arquivo não existe no repo):

```xml
<project>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
  </parent>

  <properties>
    <java.version>25</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <!-- jjwt: fora do BOM do Spring Boot, versão fixada manualmente -->
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.13.0</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

## Integrações relacionadas

- [`spring-boot-maven.md`](../integrations/spring-boot-maven.md) — como o parent POM e o plugin de repackaging se encaixam no ciclo de build do Spring Boot.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/apache/maven-site` (documentação oficial do projeto Maven) e `/spring-projects/spring-boot` (requisito de versão mínima do Maven para uso com Spring Boot: 3.6.3+).
- **Versão consultada**: convenções gerais de configuração de `maven-compiler-plugin`/propriedades — não há uma versão única do Maven pinada pelo projeto até o momento.
- **Data da consulta**: 2026-09-02.
