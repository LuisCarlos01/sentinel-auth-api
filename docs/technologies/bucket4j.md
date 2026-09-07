# Bucket4j

## Versão e propósito

**Bucket4j** é a biblioteca Java de rate limiting escolhida para a fase `v0.5.0 — Quality & Security` (issue-mãe #6, ticket #19), baseada no algoritmo *token-bucket*. Decisão registrada em [ADR-0010](../adr/0010-in-memory-rate-limiting-on-login.md): usar Bucket4j **em modo local/em memória** (sem backend distribuído — Redis, Hazelcast, Infinispan etc.), consistente com o fato de o projeto rodar como **instância única** via Docker Compose (`docs/architecture.md`, seção "Sem camada de cache (Redis)").

O grupo Maven mudou de `com.github.vladimir-bukhtoyarov` (histórico) para **`com.bucket4j`**. Para Java 17+ (o projeto usa Java 25), o artefato correto é `com.bucket4j:bucket4j_jdk17-core`, **versão `8.14.0`** (fixada em `pom.xml`).

**Correção sobre a proveniência original deste documento**: a versão inicialmente registrada aqui via Context7 (`8.19.0`) estava incorreta — não existe essa versão publicada. A versão real mais recente, confirmada diretamente no Maven Central (`search.maven.org`) no momento da implementação, é `8.14.0`. O artefato de integração com Caffeine também tem nome diferente do inicialmente registrado: é `com.bucket4j:bucket4j_jdk17-caffeine` (prefixo `jdk17`), não `com.bucket4j:bucket4j-caffeine`.

**Decisão de implementação**: o projeto **não usa** o módulo `bucket4j_jdk17-caffeine`. Esse módulo expõe um `ProxyManager` — a mesma abstração usada pelos backends distribuídos (Redis, Hazelcast) — para permitir trocar de backend sem mudar o código que consome os buckets. Como a decisão já é permanecer em memória única (ver acima), essa camada extra de indireção não agrega valor agora (evitar abstração especulativa). Em vez disso, o projeto usa **Caffeine puro** (`com.github.benmanes.caffeine.cache.Cache<String, Bucket>`, versão `3.2.0`) para guardar e expirar automaticamente os `Bucket` do Bucket4j (`Bucket.builder()...build()`, API local simples, sem `ProxyManager`).

## Quando usar

Decisão já tomada (não um ponto em aberto):

- **Apenas `POST /api/v1/auth/login`** é limitado nesta fase. `register`, `refresh` e `logout` ficam **fora de escopo** deliberadamente — o threat model de brute force que justifica rate limiting está documentado (ou a documentar) em `docs/security-threats.md` apenas para o endpoint de login; os demais endpoints não têm esse threat model registrado ainda.
- **Chave de limitação combinada**: dois buckets independentes por requisição — um por **IP do cliente** e outro por **e-mail do corpo da requisição** — e **ambos** precisam ter capacidade disponível para a requisição prosseguir. Isso limita tanto um único IP tentando múltiplos e-mails (credential stuffing) quanto um único e-mail sendo atacado a partir de múltiplos IPs (distributed brute force sobre uma conta específica).
- **Modo em memória** (`Bucket.builder()`/`LocalBucketBuilder`, sem `ProxyManager` distribuído) — suficiente e correto para uma instância única; se o projeto evoluir para múltiplas instâncias no futuro, o rate limiting por IP/e-mail deixaria de ser globalmente consistente e a decisão precisaria ser revisitada (ex.: migrar para um `ProxyManager` distribuído — JCache, Redis etc. — em vez do `Cache` local do Caffeine usado hoje).

## Boas práticas como aplicadas neste projeto

> Esta seção é baseada em decisão documentada (grilling session + arquitetura de instância única), não em código observado — ainda não há implementação de rate limiting no projeto para confirmar o padrão real de uso.

- **Um `Bucket` por chave, não um `Bucket` global** — usar um cache local mantendo um `Bucket` (ou `BucketConfiguration`) por valor de IP e por valor de e-mail, nunca um único bucket compartilhado por todas as requisições (isso limitaria o sistema inteiro, não por cliente/conta).
- **Usar um `com.github.benmanes.caffeine.cache.Cache<String, Bucket>` puro** (`Caffeine.newBuilder().expireAfterAccess(...).build()`) para armazenar os buckets em memória, em vez de um `ConcurrentHashMap<String, Bucket>` cru — o Caffeine cuida de expiração/eviction automática de chaves antigas (IPs e e-mails que não fazem mais requisições), evitando crescimento sem limite do mapa em memória. Não usar o módulo `bucket4j_jdk17-caffeine` (`ProxyManager`) — essa camada existe para permitir trocar de backend distribuído sem mudar o código consumidor, o que não é necessário aqui (modo em memória único, sem plano de migração — evitar abstração especulativa).
- **Ambos os buckets (IP e e-mail) precisam ser consultados antes de decidir permitir a requisição** — usar `tryConsume`/`tryConsumeAndReturnRemaining` nos dois; se qualquer um dos dois estiver sem capacidade, a requisição é bloqueada. A ordem de checagem não importa para a decisão final, mas token já consumido de um bucket não deve ser "devolvido" se o segundo bucket falhar (aceitar o custo — o token do primeiro bucket já foi gasto; é um trade-off aceitável para o cenário de baixa escala do projeto, não uma preocupação de precisão de billing).
- **Capacidade e janela de refill**: 5 tentativas por minuto por bucket (tanto o de IP quanto o de e-mail), com refill completo a cada minuto — decidido em sessão de grilling com o dono do projeto. Generoso o suficiente para não incomodar um usuário legítimo errando a senha uma ou duas vezes, restritivo o bastante para inviabilizar scripts ingênuos de brute force/credential stuffing (registrar formalmente em ADR na implementação da v0.5.0).
- **Resposta de limite excedido segue RFC 9457** ([ADR-0003](../adr/0003-rfc9457-error-format.md)), com HTTP 429 — ver [`bucket4j-spring-boot.md`](../integrations/bucket4j-spring-boot.md) para o mapeamento completo.

## Anti-patterns

- Usar um único `Bucket` global para todas as requisições de login, em vez de um bucket por chave (IP/e-mail) — isso é rate limiting do sistema inteiro, não por cliente/conta, e não é o que foi decidido.
- Armazenar os buckets em um `Map` sem estratégia de expiração — em produção real (mesmo de baixa escala), isso cresce indefinidamente conforme novos IPs/e-mails aparecem.
- Aplicar rate limiting via Bucket4j em endpoints fora do escopo decidido (`register`/`refresh`/`logout`) sem antes documentar o threat model correspondente em `docs/security-threats.md` — a decisão desta fase é deliberadamente restrita a `login`.
- Introduzir um backend distribuído (Redis, Hazelcast, JCache) "só para garantir" sem necessidade real — o projeto roda como instância única; complexidade de coordenação distribuída não se justifica agora (YAGNI), e mudaria a decisão registrada aqui sem motivo.
- Deixar a resposta de 429 vazar no formato de erro default do Spring (ou texto puro, como nos exemplos oficiais do Bucket4j) em vez de `ProblemDetail` — quebraria a consistência de formato de erro da API (ADR-0003).

## Exemplo mínimo

```java
// Referência — não existe ainda no repo
// 5 tentativas/minuto por bucket (IP e e-mail) — decisão registrada acima.
Bucket ipBucket = ipBucketCache.get(clientIp, key -> Bucket.builder()
    .addLimit(limit -> limit.capacity(5).refillGreedy(5, Duration.ofMinutes(1)))
    .build());

Bucket emailBucket = emailBucketCache.get(requestEmail, key -> Bucket.builder()
    .addLimit(limit -> limit.capacity(5).refillGreedy(5, Duration.ofMinutes(1)))
    .build());

ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
ConsumptionProbe emailProbe = emailBucket.tryConsumeAndReturnRemaining(1);

if (!ipProbe.isConsumed() || !emailProbe.isConsumed()) {
    // mapear para ProblemDetail 429 — ver bucket4j-spring-boot.md
}
```

Dependências Maven (já fixadas em `pom.xml`):

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>8.14.0</version>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.0</version>
</dependency>
```

## Integrações relacionadas

- [`bucket4j-spring-boot.md`](../integrations/bucket4j-spring-boot.md) — como o Bucket4j se encaixa em um filtro do Spring Security/Spring MVC para limitar `POST /api/v1/auth/login` e a tradução do erro para RFC 9457.

## Proveniência

- **Provedor**: Context7 (levantamento inicial) + Maven Central (`search.maven.org`, verificação/correção durante a implementação do ticket #19).
- **Biblioteca**: `/bucket4j/bucket4j`.
- **Versão consultada**: `8.19.0` retornada pelo Context7 estava incorreta (não publicada); `8.14.0` confirmada no Maven Central e é a versão real fixada em `pom.xml`.
- **Data da consulta**: 2026-09-07 (Context7); correção no mesmo dia via Maven Central.
