# Spring Boot

## Versão e propósito

**Spring Boot 4.1.x**, declarado em [`docs/architecture.md`](../architecture.md#stack-técnica) como o framework núcleo do projeto — o `sentinel-auth-api` existe para demonstrar domínio de Spring Security e das práticas de engenharia associadas a uma API de autenticação.

Confirmado via Context7: existe uma tag real `v4.1.0` para `spring-projects/spring-boot`, então "Spring Boot 4.1.x" é uma versão real e atual, não uma alucinação de versão. Requisitos de sistema dessa linha (4.x): **Java 17 no mínimo, compatível até Java 26** — o que valida a escolha de **Java 25 (LTS)** declarada na mesma tabela de stack.

> **Pendência explícita**: este projeto ainda não tem `pom.xml` (Phase 0 — só arquitetura documentada, Phase 1 — bootstrap Spring Boot ainda não rodou). Tudo abaixo foi validado contra a documentação oficial via Context7, **não contra um manifesto real**. Quando a Phase 1 gerar o `pom.xml`, revalidar a versão exata do Spring Boot, do Spring Framework e do Spring Security efetivamente resolvidos pelo BOM.

## Quando usar

É o framework de aplicação de toda a API — não há alternativa avaliada neste projeto (a escolha do framework não está registrada como ADR porque não houve trade-off relevante a documentar: é a premissa do projeto, não uma decisão entre opções).

## Boas práticas como aplicadas neste projeto

- **Baseline de Java via propriedade única**: ao herdar de `spring-boot-starter-parent`, configurar `<java.version>25</java.version>` no `pom.xml` (o parent POM propaga isso para `maven-compiler-plugin` via `maven.compiler.release`). Ver [`docs/technologies/maven.md`](maven.md).
- **Organização por feature/domínio, não por camada técnica** — decisão explícita em `docs/architecture.md` (`auth/`, `user/`, `rbac/`, cada um com seu próprio `controller`/`service`/`repository`). Isso é compatível nativamente com o component scanning do Spring Boot: a classe `@SpringBootApplication` deve ficar num pacote raiz que englobe todos os pacotes de feature (ex.: `dev.sentinel.auth` como raiz, com `dev.sentinel.auth.auth`, `dev.sentinel.auth.user`, `dev.sentinel.auth.rbac` como subpacotes), sem necessidade de `@ComponentScan` explícito com múltiplos `basePackages`.
- **Camadas simples `Controller → Service → Repository`** (arquitetura.md) — sem Hexagonal/Clean Architecture. Isso significa: `@RestController` fino, delegando para `@Service`; regra de negócio nunca no controller.
- **Injeção via construtor**, não `@Autowired` em campo — facilita testes unitários com Mockito (ver seção de testes em `docs/architecture.md`) sem precisar de reflection.
- **`ProblemDetail` nativo do Spring** para erros no formato RFC 9457 — ver [ADR-0003](../adr/0003-rfc9457-error-format.md). Não é uma biblioteca externa: é suporte de primeira classe do `spring-web`, exposto via `ResponseEntityExceptionHandler`/`@ExceptionHandler` retornando `ProblemDetail`.
- **Jakarta Bean Validation** (`spring-boot-starter-validation`) em DTOs de entrada — ver [ADR-0004](../adr/0004-bean-validation-input.md).
- **Versionamento de rota via prefixo `/api/v1`** em todos os `@RequestMapping`/`@RestController` — ver [ADR-0002](../adr/0002-uri-based-api-versioning.md).
- **Actuator habilitado desde a v0.1.0** (`spring-boot-starter-actuator`) para observabilidade básica, conforme `docs/architecture.md`.
- **Autenticação stateless** — sem `HttpSession` para autenticação; o estado de sessão é substituído pelo par access/refresh token (ver [`docs/technologies/jjwt.md`](jjwt.md) e [`docs/integrations/spring-boot-jjwt.md`](../integrations/spring-boot-jjwt.md)).

## Anti-patterns

- Pacotes globais por camada técnica (`controllers/`, `services/`, `repositories/` cortando toda a aplicação) — decisão explícita do projeto de **não** fazer isso.
- Lógica de negócio em `@RestController` — deve estar em `@Service`.
- Adicionar Hexagonal/Clean Architecture (ports & adapters, use cases) — avaliado e descartado conscientemente por complexidade desnecessária frente ao escopo (KISS/YAGNI, ver `docs/architecture.md`).
- Deixar o Spring Security responder erros com o corpo padrão dele (não é `ProblemDetail` por padrão) — é preciso customizar `AuthenticationEntryPoint`/`AccessDeniedHandler` para manter consistência com a RFC 9457 mesmo nos erros 401/403 gerados pelo filtro de segurança.
- Fixar manualmente versões de dependências geridas pelo BOM do Spring Boot (`spring-boot-dependencies`) sem necessidade — quebra a combinação testada pelo próprio Spring Boot.

## Exemplo mínimo

Estrutura de pacote e um controller ilustrativos (referência para a Phase 1 — nada disto existe no repo ainda):

```java
// dev.sentinel.auth.auth.AuthController
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

## Integrações relacionadas

- [`spring-boot-argon2id.md`](../integrations/spring-boot-argon2id.md) — encoding de senha via Spring Security Crypto.
- [`spring-boot-jjwt.md`](../integrations/spring-boot-jjwt.md) — emissão/validação de JWT no filtro de segurança.
- [`spring-boot-flyway.md`](../integrations/spring-boot-flyway.md) — migrações automáticas no startup.
- [`spring-boot-maven.md`](../integrations/spring-boot-maven.md) — empacotamento e gestão de dependências.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/spring-projects/spring-boot` (também consultada a versão travada `/spring-projects/spring-boot/v4.1.0`).
- **Versão consultada**: tag `v4.1.0` confirmada como existente; requisitos de sistema (Java 17–26) confirmados no branch `main` da documentação (`system-requirements.adoc`), consistente entre as duas consultas.
- **Data da consulta**: 2026-09-02.
- **Limitação observada**: ao consultar `/spring-projects/spring-boot/v4.1.0` por trechos de código (ex.: versão do Spring Security no BOM), o Context7 retornou conteúdo do branch `main` do repositório (mostrando `Spring Security 7.1.1-SNAPSHOT`, referente a um Spring Boot 4.2.0-SNAPSHOT), não o conteúdo travado na tag `v4.1.0`. Ou seja, o versionamento de *code snippets* desse repositório no Context7 não é confiável para números de versão exatos de dependências transitivas — **a versão exata do Spring Framework e do Spring Security usados por Spring Boot 4.1.x precisa ser confirmada depois, direto no `pom.xml` gerado (dependency:tree) na Phase 1**, não apenas via Context7. O mesmo padrão se repetiu ao investigar a matriz de CI/teste de versões de Java: o snippet retornado (mostrando Java 17/21/25/26 como targets de build) cita como fonte explícita `github.com/spring-projects/spring-boot/blob/main/.github/workflows/ci.yml` — ou seja, também veio do branch `main`, não de um conteúdo travado em `v4.1.0`. Por isso a afirmação "a matriz de CI já testa explicitamente contra Java 25" foi removida do corpo do texto: é plausível (o `ci.yml` de `main` já lista Java 25 e 26 como targets), mas não tem a mesma cobertura de verificação que os itens confirmados como consistentes entre tag e `main` (Java 17 mínimo, requisitos de sistema).
