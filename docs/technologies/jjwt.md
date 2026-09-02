# jjwt (io.jsonwebtoken)

## Versão e propósito

**`jjwt`** (`io.jsonwebtoken`) é a biblioteca JWT declarada em [`docs/architecture.md`](../architecture.md#stack-técnica) ("biblioteca JWT usada desde a v0.1.0"). A tabela de stack **não fixa uma versão específica** — este documento registra a versão mais recente confirmada via Context7 no momento da consulta: **0.13.0** (declarada como `project-version` no `README.adoc` oficial do projeto).

`jjwt` é dividida em três artefatos Maven, que devem ser sempre declarados juntos:

| Artefato | Escopo | Papel |
|---|---|---|
| `jjwt-api` | `compile` | API pública, estável, semver garantido |
| `jjwt-impl` | `runtime` | Implementação interna — **nunca** depender dela em `compile` |
| `jjwt-jackson` (ou `jjwt-gson`) | `runtime` | Serialização JSON do payload |

Como o projeto já traz Jackson transitivamente via Spring Web (`spring-boot-starter-web`), `jjwt-jackson` é a escolha natural — evita adicionar Gson como uma segunda biblioteca JSON no classpath.

> **Pendência explícita**: sem `pom.xml`, a versão real usada pelo projeto ainda não está fixada. Confirmar `0.13.0` (ou a versão mais recente disponível) quando a Phase 1 rodar.

## Quando usar

No emissão e validação dos tokens do fluxo de autenticação (`docs/architecture.md`, seção "Fluxo de autenticação e ciclo de vida de tokens"):

- **Access token**: 15 minutos de validade, sem persistência em banco (stateless).
- **Refresh token**: 7 dias, com rotação a cada uso, **persistido em tabela no PostgreSQL** para permitir revogação real no `logout`.

**Ponto em aberto, não uma decisão registrada**: `docs/architecture.md` não especifica se o refresh token em si é um JWT assinado via `jjwt` (com um `jti` referenciado na tabela de persistência) ou um token opaco gerado por outro meio (ex.: `UUID`/valor aleatório) e apenas armazenado/validado por lookup direto no banco. Ambas as abordagens são compatíveis com a stack declarada. Essa escolha específica é uma decisão de implementação da fase `v0.3.0`/`v0.4.0` do roadmap (Authentication Core / Authorization & Token Lifecycle), não coberta por nenhum ADR ainda — este documento não a antecipa.

## Boas práticas como aplicadas neste projeto

- **Claims do access token alinhadas ao modelo de domínio**: `sub` (identificador do usuário/e-mail), roles como claim customizada (para suportar as checagens `hasRole()` do RBAC enxuto — [ADR-0001](../adr/0001-lean-rbac-modeling.md)), `iat` e `exp`.
- **`exp` derivado diretamente dos tempos de vida documentados** (15 min access / 7 dias refresh, se o refresh também for um JWT) — nunca hardcoded em múltiplos lugares; centralizar em uma constante/config.
- **API de builder/parser fluente do jjwt** (`Jwts.builder()...signWith(...)`, `Jwts.parser()...build().parse...`) em vez de manipular Base64/HMAC manualmente.
- **Chave de assinatura fora do código-fonte**: carregada de variável de ambiente/secret (nunca commitada), consistente com a seção "Segurança operacional e segredos" de `docs/architecture.md`. Para HMAC (`HS256`), a chave deve ter no mínimo 256 bits — `Keys.hmacShaKeyFor(bytes)` do próprio jjwt já lança exceção se a chave for curta demais, o que funciona como uma checagem defensiva gratuita.
- **Nunca colocar dado sensível em claim** — o payload de um JWT é apenas Base64, não é criptografado; assinatura garante integridade, não confidencialidade. Nunca incluir hash de senha ou dado de PII além do necessário para autorização.
- **Erros de validação do jjwt (`ExpiredJwtException`, `SignatureException`, etc.) devem ser convertidos para o formato RFC 9457** ([ADR-0003](../adr/0003-rfc9457-error-format.md)) no filtro de segurança, e não vazar como stack trace ou como o corpo de erro default do Spring Security.

## Anti-patterns

- Declarar `jjwt-impl` com escopo `compile` — deve ser sempre `runtime`, porque é uma API interna que pode mudar sem aviso entre versões, ao contrário de `jjwt-api`.
- Guardar dado sensível ou grande volume de dado no payload do token (aumenta o tamanho do JWT em toda requisição, sem necessidade).
- Reimplementar parsing/verificação de assinatura na mão em vez de usar `Jwts.parser()`.
- Usar a mesma chave de assinatura para access token e para outro propósito não relacionado (ex.: assinar outro tipo de token do sistema) — chave de assinatura deve ser exclusiva do propósito de autenticação desta API.
- Ignorar a exceção de expiração e tratar qualquer falha de parsing como "token inválido" genérico — perde-se a distinção entre "expirou" (cliente deveria tentar refresh) e "assinatura inválida"/"malformado" (cliente não deveria tentar refresh).

## Exemplo mínimo

```java
// Emissão (referência — não existe ainda no repo)
SecretKey key = Keys.hmacShaKeyFor(signingKeyBytes); // >= 256 bits, de variável de ambiente

String accessToken = Jwts.builder()
    .subject(user.getEmail())
    .claim("roles", user.getRoleNames())
    .issuedAt(new Date())
    .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
    .signWith(key, Jwts.SIG.HS256)
    .compact();

// Validação
JwtParser parser = Jwts.parser().verifyWith(key).build();
try {
    Jws<Claims> jws = parser.parseSignedClaims(accessToken);
    String email = jws.getPayload().getSubject();
} catch (ExpiredJwtException ex) {
    // mapear para ProblemDetail 401 — token expirado
} catch (JwtException ex) {
    // mapear para ProblemDetail 401 — token inválido
}
```

Dependências Maven (a confirmar/pinar na Phase 1):

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.13.0</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
```

## Integrações relacionadas

- [`jjwt-spring-boot.md`](../integrations/jjwt-spring-boot.md) — como o jjwt se encaixa no filtro de segurança do Spring Security e na conversão de erros para RFC 9457.

## Proveniência

- **Provedor**: Context7.
- **Biblioteca**: `/jwtk/jjwt`.
- **Versão consultada**: `0.13.0` (declarada em `:project-version:` no `README.adoc` do repositório, no momento da consulta). O Context7 não listou múltiplas versões indexadas para esta biblioteca (ao contrário de `/spring-projects/spring-boot`) — a consulta reflete o estado mais recente do repositório, não uma tag específica travada.
- **Data da consulta**: 2026-09-02.
