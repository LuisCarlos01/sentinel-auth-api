# 0010 — Rate limiting em memória no login, via Bucket4j

## Status

Accepted

## Contexto

ADR-0005 adiou deliberadamente o rate limiting da v0.1.0 para a fase `v0.5.0 — Quality & Security`, aceitando conscientemente o risco de brute force/credential stuffing em `login` até essa fase (`docs/security-threats.md`). Chegada a v0.5.0, esse trade-off precisa ser fechado com uma decisão concreta de mecanismo, escopo e parâmetros.

O projeto roda como **instância única** via Docker Compose (`docs/architecture.md`) — não há múltiplas réplicas nem um requisito de rate limiting globalmente consistente entre instâncias. `docs/architecture.md` também já registra a ausência de uma camada de cache distribuído (Redis) na v0.1.0, sem fechar a porta para o futuro.

## Decisão

Rate limiting implementado com **Bucket4j** (algoritmo token-bucket), em **modo local/em memória** — sem backend distribuído (Redis, Hazelcast, JCache). Aplicado **somente a `POST /api/v1/auth/login`**: é o único endpoint com o threat model de brute force/credential stuffing documentado em `docs/security-threats.md`; `register`, `refresh` e `logout` ficam fora do escopo desta decisão.

Chave de limitação **combinada**: um bucket por **IP do cliente** e outro por **e-mail do corpo da requisição**, ambos precisando ter capacidade disponível para a requisição prosseguir. Isso cobre tanto um único IP atacando múltiplas contas quanto uma única conta atacada a partir de múltiplos IPs.

Capacidade: **5 tentativas por minuto**, por bucket, com refill completo a cada minuto. Excedido o limite (de qualquer um dos dois buckets), a resposta é `429 Too Many Requests` no formato RFC 9457 (ADR-0003), com mensagem genérica — não revela se foi o bucket de IP ou o de e-mail que estourou, mesma filosofia já aplicada ao erro de credenciais inválidas do `login`.

### Alternativas consideradas

- **Backend distribuído (Redis/Hazelcast) desde já**: descartado — o projeto roda como instância única; coordenação distribuída não tem nenhum requisito real a atender agora (YAGNI). Bucket4j suporta os dois modos com a mesma API, então essa porta continua aberta se o projeto migrar para múltiplas instâncias no futuro, sem precisar trocar de biblioteca.
- **Chave só por IP**: descartado — não protege uma conta específica sendo atacada a partir de múltiplos IPs (ex.: botnet).
- **Chave só por e-mail**: descartado — não limita um único IP tentando credential stuffing contra várias contas diferentes.
- **Aplicar rate limiting também a `register`/`refresh`/`logout`**: descartado por falta de threat model documentado para esses endpoints — estender o escopo sem essa base seria uma decisão de segurança inventada, não fundamentada.

## Consequências

**Positivas**

- Fecha o risco aceito conscientemente em ADR-0005 sem introduzir infraestrutura nova (Redis) que o projeto não precisa hoje.
- Chave combinada (IP + e-mail) cobre os dois vetores de ataque mais comuns contra `login` sem um ponto cego óbvio.
- Mensagem de erro genérica mantém a mesma postura de não vazamento de informação já aplicada ao restante do fluxo de autenticação.

**Trade-offs aceitos**

- Rate limiting não é globalmente consistente entre instâncias — se o projeto rodar múltiplas réplicas no futuro, cada uma tem seus próprios buckets em memória, permitindo até `5 × N réplicas` tentativas efetivas. Aceitável para o desenho atual de instância única; precisa ser revisitado se isso mudar.
- Os buckets em memória são perdidos a cada reinício da aplicação — um atacante bloqueado recupera tentativas após um deploy/restart. Risco aceito: reinícios não são frequentes o suficiente para tornar isso um vetor prático de bypass.
- `5` tentativas/minuto é um valor calibrado por julgamento (generoso para erro humano, restritivo para script ingênuo), não por análise de dados reais de tráfego — o projeto não tem usuários reais para calibrar contra.

## Referências

- [ADR-0005](0005-defer-rate-limiting.md) — decisão original de adiamento, fechada por este ADR.
- [ADR-0003](0003-rfc9457-error-format.md) — formato de erro RFC 9457, aplicado ao `429`.
- [`docs/technologies/bucket4j.md`](../technologies/bucket4j.md) e [`docs/integrations/bucket4j-spring-boot.md`](../integrations/bucket4j-spring-boot.md) — referência técnica da biblioteca.
- [`docs/security-threats.md`](../security-threats.md) — threat model de brute force/credential stuffing que motiva esta decisão.
