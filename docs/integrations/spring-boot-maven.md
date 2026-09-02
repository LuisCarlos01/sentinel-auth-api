# Spring Boot + Maven

## Responsabilidade de cada tecnologia

- **Maven**: ferramenta de build genérica — resolve dependências, compila, roda testes, empacota. Não sabe nada sobre Spring por si só.
- **Spring Boot**: fornece um **parent POM** (`spring-boot-starter-parent`) e um **plugin** (`spring-boot-maven-plugin`) que especializam o Maven para o ecossistema Spring — gestão de versões via BOM e empacotamento em JAR executável ("fat jar" com servidor embutido).

## Fluxo entre elas

1. O `pom.xml` herda de `spring-boot-starter-parent`, o que importa o BOM `spring-boot-dependencies` — todas as dependências `spring-boot-starter-*` deixam de precisar de `<version>` explícita.
2. `mvn package` compila o código e, via `spring-boot-maven-plugin`, gera um JAR executável (com todas as dependências embutidas e um servidor Tomcat embarcado) em vez de um JAR "fino" tradicional.
3. Esse JAR executável é exatamente o artefato que o `Dockerfile` (build multi-stage) copia para a imagem final — a integração Maven + Spring Boot é o que torna a imagem Docker possível sem passos manuais de empacotamento.
4. Em CI (GitHub Actions), o mesmo comando (`mvn verify` ou equivalente) roda compilação, testes unitários (Mockito) e testes de integração (Testcontainers) antes de qualquer build de imagem Docker.

## Configuração necessária

- `<parent>` apontando para `spring-boot-starter-parent` na versão 4.1.x.
- `<properties><java.version>25</java.version></properties>` — propagado pelo parent para `maven-compiler-plugin`.
- `<plugin>spring-boot-maven-plugin</plugin>` declarado em `<build><plugins>`, sem necessidade de `<version>` (também gerido pelo parent).

## Cuidados e anti-patterns específicos dessa combinação

- **Não declarar `<version>` manualmente em dependências geridas pelo BOM** — isso destrava a combinação de versões testada pelo time do Spring Boot e pode gerar incompatibilidade silenciosa entre módulos Spring.
- **Não esquecer que dependências fora do BOM do Spring Boot** (como os artefatos do `jjwt` — ver [`spring-boot-jjwt.md`](spring-boot-jjwt.md)) **precisam de `<version>` explícita** — o parent não as gerencia.
- **Divergência entre a versão de Java local (usada pelo dev) e a usada em CI/imagem Docker** é a causa mais comum de "funciona na minha máquina": fixar a mesma baseline (Java 25) em `<java.version>`, no workflow do GitHub Actions (`setup-java`) e na imagem base do `Dockerfile` evita esse tipo de divergência.
