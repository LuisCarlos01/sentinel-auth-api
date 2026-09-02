# Spring Boot + jjwt

## Responsabilidade de cada tecnologia

- **Spring Boot / Spring Security**: dono do pipeline de request — filtro de segurança (`OncePerRequestFilter`/`AuthenticationFilter` customizado) que intercepta a requisição, extrai o token do header `Authorization`, e popula o `SecurityContext` se o token for válido. Também é responsável por transformar falha de autenticação em resposta HTTP (idealmente RFC 9457 — [ADR-0003](../adr/0003-rfc9457-error-format.md)).
- **jjwt**: biblioteca de baixo nível que constrói (`Jwts.builder()`) e verifica (`Jwts.parser()`) o token em si — assinatura, expiração, claims. Não tem nenhuma integração nativa/automática com Spring Boot (ao contrário do Flyway, que tem autoconfiguração própria) — a integração é inteiramente escrita à mão neste projeto.

## Fluxo entre elas

1. **Emissão** (`login`/`refresh`): o service de autenticação, após validar credenciais (ver [`argon2id-spring-boot.md`](argon2id-spring-boot.md)), monta claims (`sub`, `roles`, `iat`, `exp`) e chama `Jwts.builder()...signWith(key, Jwts.SIG.HS256).compact()` para gerar o access token. Ver [`jjwt.md`](../technologies/jjwt.md) para o ponto em aberto sobre se o refresh token também é um JWT ou um token opaco.
2. **Validação por requisição**: um filtro Spring Security customizado (registrado na `SecurityFilterChain`, antes do filtro padrão de autenticação) lê o header `Authorization: Bearer <token>`, chama `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`, e, se válido, constrói um `Authentication`/`UserDetails` a partir das claims (incluindo as roles, para as checagens `hasRole()` do RBAC enxuto — [ADR-0001](../adr/0001-lean-rbac-modeling.md)) e o coloca no `SecurityContextHolder`.
3. **Falha de validação**: exceções do jjwt (`ExpiredJwtException`, `SignatureException`, `MalformedJwtException`) devem ser capturadas dentro do filtro (elas não chegam automaticamente a um `@ExceptionHandler` de controller, porque o filtro roda antes do dispatcher) e traduzidas manualmente para uma resposta `ProblemDetail` 401, via um `AuthenticationEntryPoint` customizado.
4. **Refresh**: no endpoint `/api/v1/auth/refresh`, o refresh token recebido é validado (via jjwt se for um JWT, ou via lookup no banco se for opaco) e, sendo válido e não revogado, o anterior é invalidado no PostgreSQL (rotação — `docs/architecture.md`) e um novo par de tokens é emitido.

## Configuração necessária

- Dependências: `jjwt-api` (compile), `jjwt-impl` (runtime), `jjwt-jackson` (runtime) — ver [`jjwt.md`](../technologies/jjwt.md).
- Uma chave de assinatura HMAC de pelo menos 256 bits, carregada de variável de ambiente/secret (nunca commitada — `docs/architecture.md`, seção "Segurança operacional e segredos").
- Um filtro de segurança customizado registrado na `SecurityFilterChain` do Spring Security (não existe autoconfiguração pronta para isso — é código próprio do projeto).
- Um `AuthenticationEntryPoint`/`AccessDeniedHandler` customizado para que erros 401/403 originados do filtro JWT sigam o mesmo formato RFC 9457 usado pelo restante da API.

## Cuidados e anti-patterns específicos dessa combinação

- **Não deixar o filtro JWT lançar exceção não tratada** — por rodar antes do `DispatcherServlet`, uma exceção do jjwt não capturada dentro do filtro não é interceptada pelos `@ExceptionHandler`/`@ControllerAdvice` normais do Spring MVC, resultando em um erro genérico do container em vez de um `ProblemDetail` consistente.
- **Registrar o filtro JWT na ordem certa da `SecurityFilterChain`** — deve rodar antes do filtro que checa autorização por role, senão a checagem de RBAC não tem `Authentication` populada para avaliar.
- **Não confundir a validade do access token (15 min) com a do refresh token (7 dias) na configuração da chave/`exp`** — são dois tempos de vida distintos e documentados; usar a constante errada em um dos dois é um bug silencioso (token expira cedo ou tarde demais em relação ao que a arquitetura descreve).
- **Testes de integração** (Testcontainers) do fluxo completo `login → chamada autenticada → refresh → logout` são o lugar certo para pegar problemas de integração entre o filtro Spring Security e o parsing jjwt, já que é uma integração sem autoconfiguração pronta e, portanto, mais sujeita a erro de "fiação" manual.
