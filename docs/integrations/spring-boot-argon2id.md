# Spring Boot + Argon2id

## Responsabilidade de cada tecnologia

- **Spring Boot / Spring Security**: fornece o contrato `PasswordEncoder`, o mecanismo de injeção de dependência para expor a implementação como bean, e o ponto de extensão (`spring-security-crypto`) onde `Argon2PasswordEncoder` vive.
- **Argon2id**: o algoritmo de hashing em si, executado dentro de `Argon2PasswordEncoder` — decide como transformar senha em texto plano em hash irreversível e como comparar senha informada contra hash armazenado.

## Fluxo entre elas

1. Na inicialização do contexto Spring, um `@Bean PasswordEncoder` expõe `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.
2. No `register` (`POST /api/v1/auth/register`), o service de autenticação recebe a senha em texto plano do DTO validado (via Bean Validation — [ADR-0004](../adr/0004-bean-validation-input.md)), chama `passwordEncoder.encode(rawPassword)`, e persiste apenas o hash resultante no campo de senha do `User` (`docs/architecture.md`, modelo de domínio).
3. No `login` (`POST /api/v1/auth/login`), o service busca o `User` pelo `email`, e chama `passwordEncoder.matches(rawPassword, storedHash)` — nunca decodifica ou reconstrói a senha original.
4. Se as credenciais forem inválidas, a resposta de erro segue o formato RFC 9457 ([ADR-0003](../adr/0003-rfc9457-error-format.md)), sem detalhar se foi o e-mail ou a senha que falhou (evita enumeração de usuários).

## Configuração necessária

- Dependência: `spring-boot-starter-security` (traz `spring-security-crypto` transitivamente) — nenhuma dependência adicional além dela.
- Um único `@Bean PasswordEncoder` no contexto Spring — se mais de um bean `PasswordEncoder` for declarado sem qualificador, o Spring falha a injeção por ambiguidade.
- Nenhuma configuração de propriedade (`application.yml`) é necessária — os parâmetros de custo do Argon2id são definidos em código, na criação do encoder.

## Cuidados e anti-patterns específicos dessa combinação

- **Não deixar o Spring Security cair no autodetect de `DelegatingPasswordEncoder`** (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`) sem intenção — esse helper usa **bcrypt** como algoritmo padrão de encode, não Argon2id. Se o bean `PasswordEncoder` não for declarado explicitamente com `Argon2PasswordEncoder`, o projeto silenciosamente deixa de usar a stack declarada.
- **Consistência entre o encoder usado no `AuthenticationProvider`/`UserDetailsService` (se o fluxo usar essas abstrações do Spring Security) e o encoder usado manualmente no service de `register`** — devem ser exatamente o mesmo bean, para que hash gerado no registro seja compreendido na validação do login.
- **Testes de integração (Testcontainers)** que exercitam `register` → `login` de ponta a ponta validam implicitamente que o par encode/matches está consistente — vale manter pelo menos um teste assim, já que é o ponto onde uma divergência de configuração do encoder mais provavelmente apareceria.
