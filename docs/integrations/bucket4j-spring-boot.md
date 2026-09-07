# Bucket4j + Spring Boot

## Responsabilidade de cada tecnologia

- **Spring Boot / Spring Security**: dono do pipeline de request — um filtro (`OncePerRequestFilter`, registrado na `SecurityFilterChain` **antes** da lógica de autenticação de credenciais) intercepta especificamente `POST /api/v1/auth/login`, extrai IP do cliente e e-mail do corpo da requisição, e decide se a requisição segue adiante ou é bloqueada. Também é responsável por traduzir o bloqueio em uma resposta HTTP no formato RFC 9457 ([ADR-0003](../adr/0003-rfc9457-error-format.md)), da mesma forma que já é feito para erros de autenticação JWT (ver [`jjwt-spring-boot.md`](jjwt-spring-boot.md)).
- **Bucket4j**: biblioteca de baixo nível que mantém o estado dos buckets (um por IP, um por e-mail) e decide, via `tryConsume`/`tryConsumeAndReturnRemaining`, se há capacidade disponível. Não tem autoconfiguração própria no Spring Boot (não é um starter oficial mantido pelo Spring nem pelo Bucket4j) — a integração é escrita à mão neste projeto, mesmo padrão do jjwt.

## Fluxo entre elas

1. Uma requisição chega em `POST /api/v1/auth/login`.
2. Um filtro customizado (ex.: `LoginRateLimitFilter`), registrado na `SecurityFilterChain` **antes** de qualquer autenticação de credenciais e **restrito à rota de login** (não deve interceptar `register`/`refresh`/`logout` — decisão explícita de escopo desta fase), extrai:
   - o IP do cliente (via `HttpServletRequest.getRemoteAddr()` ou, se o projeto rodar atrás de proxy reverso no futuro, o cabeçalho `X-Forwarded-For` — a decidir na implementação, não coberta ainda por `docs/architecture.md`);
   - o e-mail do corpo da requisição (exige ler o body antecipadamente — cuidado com o corpo da requisição já ter sido consumido antes de chegar ao `@RequestBody` do controller; normalmente resolvido com um `ContentCachingRequestWrapper` ou parseando o JSON uma vez no próprio filtro).
3. O filtro consulta (ou cria, se ainda não existir) o `Bucket` correspondente ao IP e o `Bucket` correspondente ao e-mail, usando um `com.github.benmanes.caffeine.cache.Cache<String, Bucket>` puro (ver [`bucket4j.md`](../technologies/bucket4j.md)) como armazenamento em memória com eviction automática.
4. Se **ambos** os buckets tiverem capacidade, o filtro consome um token de cada e deixa a requisição seguir para o `AuthenticationManager`/service de login normal.
5. Se **qualquer um** dos dois buckets estiver sem capacidade, o filtro interrompe a cadeia e escreve diretamente a resposta HTTP 429 no formato `ProblemDetail` (RFC 9457) — sem deixar a requisição chegar ao controller/service de autenticação.

## Configuração necessária

- Dependência `com.bucket4j:bucket4j_jdk17-core` (compile) — ver [`bucket4j.md`](../technologies/bucket4j.md).
- `com.github.ben-manes.caffeine:caffeine` (compile) para o cache local com eviction automática — não é trazido transitivamente por nenhum starter do projeto (`pom.xml` não declarava Spring Cache/Caffeine antes deste ticket). O módulo `bucket4j_jdk17-caffeine` (com seu `ProxyManager`) não é usado — ver `bucket4j.md`.
- Um filtro Spring Security customizado (`OncePerRequestFilter`), registrado na `SecurityFilterChain`, restrito à rota `POST /api/v1/auth/login` (via `RequestMatcher`, não aplicado globalmente).
- Capacidade e janela de refill dos buckets: **5 tentativas por minuto**, por bucket (IP e e-mail) — ver [`bucket4j.md`](../technologies/bucket4j.md), seção "Boas práticas".
- Tradução da falha de limite para `ProblemDetail`: `status=429`, `title` claro (ex.: "Too Many Requests"), e opcionalmente um `detail`/header (`Retry-After` ou `X-Rate-Limit-Retry-After-Seconds`, disponível via `ConsumptionProbe.getNanosToWaitForRefill()`) indicando quando tentar novamente — mesmo racional de consistência de erro já usado para os 401 do filtro JWT.

## Cuidados e anti-patterns específicos dessa combinação

- **Ordem do filtro na `SecurityFilterChain`**: o filtro de rate limiting deve rodar **antes** de qualquer tentativa de autenticação de credenciais (Argon2id — ver [`argon2id-spring-boot.md`](argon2id-spring-boot.md)), para não gastar CPU/tempo verificando senha de uma requisição que já deveria ser bloqueada por excesso de tentativas.
- **Restringir o filtro à rota de login explicitamente** (`RequestMatcher` por path + método HTTP) — registrar o filtro sem essa restrição faria com que ele rodasse (inutilmente, ou pior, incorretamente) em `register`/`refresh`/`logout`, contrariando a decisão de escopo desta fase.
- **Ler o corpo da requisição para extrair o e-mail sem quebrar o `@RequestBody` do controller downstream** — um filtro que consome o `InputStream` da requisição sem envolvê-lo em um wrapper reutilizável (`ContentCachingRequestWrapper` ou equivalente) faz o parsing do JSON no controller falhar por corpo vazio. Esse é o ponto de maior risco técnico desta integração, por não haver suporte nativo do Spring para "ler o body num filtro e reaproveitar no controller" sem esforço extra.
- **Não deixar o filtro lançar exceção não tratada** — mesmo cuidado já registrado para o filtro JWT (ver [`jjwt-spring-boot.md`](jjwt-spring-boot.md)): por rodar antes do `DispatcherServlet`, uma exceção não capturada dentro do filtro de rate limiting não passa pelos `@ExceptionHandler`/`@ControllerAdvice` normais.
- **Testes de carga (Gatling — ver [`gatling-maven.md`](gatling-maven.md)) precisam estar cientes deste rate limiting** — uma simulação de carga contra `login` sem variar IP/e-mail por usuário virtual vai bater no 429 rapidamente e reportar "degradação" que na verdade é o comportamento correto e esperado.
- **Buckets em memória são por instância da aplicação** — como o projeto roda como instância única (Docker Compose), isso é consistente com a arquitetura atual; se o projeto vier a escalar horizontalmente no futuro, o rate limiting por IP/e-mail deixaria de ser globalmente correto (cada instância teria seu próprio contador) e a decisão de "em memória" precisaria ser revisitada — não é um cuidado a resolver agora, apenas uma consequência a ter em mente.
