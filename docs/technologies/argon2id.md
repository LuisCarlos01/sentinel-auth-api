# Argon2id

## Versão e propósito

**Argon2id** é o algoritmo de hashing de senha declarado em [`docs/architecture.md`](../architecture.md#stack-técnica) ("algoritmo moderno recomendado para hashing de senha"). Não é uma biblioteca própria: no ecossistema Spring, é implementado por `Argon2PasswordEncoder`, classe do módulo **`spring-security-crypto`** — trazido transitivamente por `spring-boot-starter-security`, cuja versão exata é gerida pelo BOM do Spring Boot (ver pendência em [`spring-boot.md`](spring-boot.md)). Internamente, essa implementação usa Bouncy Castle.

Argon2id é a variante híbrida do algoritmo vencedor da Password Hashing Competition — combina resistência a ataques por GPU/ASIC (memory-hard, como o Argon2d) com resistência a side-channel timing attacks (como o Argon2i). É a variante recomendada por padrão para hashing de senha de uso geral (ao contrário do Argon2i puro ou Argon2d puro).

Cobre o campo "senha (hash)" do modelo `User` em `docs/architecture.md`: "Via Argon2id — nunca em texto plano". Não há um ADR dedicado a essa decisão hoje — está registrada apenas na tabela de stack e no modelo de domínio de `docs/architecture.md`.

## Quando usar

Em qualquer ponto do fluxo de autenticação que grave ou compare senha de usuário: `register` (encode) e `login` (matches). Ver endpoints em `docs/architecture.md`.

## Boas práticas como aplicadas neste projeto

- **Nunca implementar Argon2 na mão** (ex.: chamar Bouncy Castle diretamente) — usar `Argon2PasswordEncoder`, já testado e mantido pelo time do Spring Security.
- **Expor como bean `PasswordEncoder`** (`@Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }`) e injetar via construtor no service de autenticação — nunca instanciar `new Argon2PasswordEncoder(...)` disperso pelo código.
- **Parâmetros default do Spring Security (`defaultsForSpringSecurity_v5_8()`)** como ponto de partida razoável: salt de 16 bytes, hash de 32 bytes, paralelismo 1, custo de memória `1 << 14` (16 MB), 2 iterações. Para a escala do projeto (dezenas de usuários, portfólio/estudo, sem SLA de throughput), esses defaults são adequados; não há necessidade de tuning agressivo de custo de memória/iterações, mas isso é um ponto a reavaliar explicitamente se a fase `v0.5.0 — Quality & Security` do roadmap tratar de hardening.
- **Formato self-describing**: o hash gerado por `Argon2PasswordEncoder` já embute salt, versão do algoritmo e parâmetros de custo na própria string de saída — não há necessidade de uma coluna de `salt` separada na tabela `User`, nem de guardar os parâmetros de custo à parte.
- **Nunca comparar hashes com `.equals()`** — sempre `passwordEncoder.matches(rawPassword, encodedPassword)`, que faz a comparação de forma segura contra timing attacks e sabe reconstruir os parâmetros a partir do hash armazenado.
- **`Argon2PasswordEncoder` direto, sem `DelegatingPasswordEncoder`**: como o projeto não tem senhas legadas de outro algoritmo para migrar, não há necessidade da camada de prefixo `{id}` do `DelegatingPasswordEncoder` — usar o encoder de Argon2id diretamente é mais simples e já é a decisão registrada (um único algoritmo, sem múltiplos legados).

## Anti-patterns

- Persistir hash de senha ou senha em texto plano em log, exceção ou claim de JWT — nunca.
- Trocar os parâmetros de custo (memória/iterações/paralelismo) em produção sem uma estratégia de rehash, já que hashes antigos com parâmetros diferentes continuam válidos (o formato é self-describing), mas hashes novos passam a ter custo diferente — se os parâmetros mudarem, considerar rehash oportunista no login (verificar `encoder.upgradeEncoding(hash)` antes de decidir regravar).
- Usar `BCryptPasswordEncoder` ou `NoOpPasswordEncoder` "só pra testar" e esquecer de trocar — a decisão de stack já fixou Argon2id, não bcrypt.
- Comparar senha antes de checar se o usuário existe de forma que vaze timing/enumeração de e-mail — fora do escopo deste documento (é uma preocupação de fluxo de autenticação, não do encoder em si), mas relevante ao integrar com o endpoint de `login`.

## Exemplo mínimo

```java
@Configuration
class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}

// Uso no service (referência — não existe ainda no repo)
class AuthService {

    private final PasswordEncoder passwordEncoder;

    AuthService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    void register(String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        // persistir `hash`, nunca `rawPassword`
    }

    boolean login(String rawPassword, String storedHash) {
        return passwordEncoder.matches(rawPassword, storedHash);
    }
}
```

## Integrações relacionadas

- [`argon2id-spring-boot.md`](../integrations/argon2id-spring-boot.md) — como o bean é registrado e consumido dentro do Spring Security.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/websites/spring_io_spring-security_reference_7_0` (documentação versionada do Spring Security 7.0).
- **Versão consultada**: Spring Security 7.0 (referência oficial versionada). A versão exata do Spring Security resolvida pelo BOM do Spring Boot 4.1.x **não foi confirmada com certeza** (ver limitação em [`spring-boot.md`](spring-boot.md)) — é razoável assumir Spring Security 7.x, mas o minor exato fica pendente de confirmação via `pom.xml`/`dependency:tree` real na Phase 1.
- **Data da consulta**: 2026-09-02.
