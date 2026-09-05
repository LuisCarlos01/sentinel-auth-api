# 0007 — Pacote técnico compartilhado para tratamento de erro RFC 9457

## Status

Accepted

## Contexto

O ADR-0003 estabelece que todas as respostas de erro da API seguem RFC 9457 via `ProblemDetail`, mas não decide onde vive a infraestrutura que faz essa tradução — porque, até este ciclo, nenhum controller existia no projeto. `POST /api/v1/auth/register` é o primeiro endpoint real e o primeiro ponto em que essa lacuna precisa ser fechada.

A convenção do projeto (`CLAUDE.md`, `docs/architecture.md`) é organização por feature/domínio, não por camada técnica global — cada pacote (`auth/`, `user/`, `rbac/`) contém suas próprias camadas internas, evitando um `controllers/`/`services/` cortando a aplicação inteira. Um `@RestControllerAdvice` central para tradução de exceções é, por natureza, uma camada técnica que corta todos os domínios — o oposto do padrão adotado até aqui.

## Decisão

O tratamento de erro RFC 9457 vive num pacote técnico compartilhado (`dev.sentinel.auth.common.web`), com um único `@RestControllerAdvice` cobrindo, no mínimo, `MethodArgumentNotValidException` (→ `400`) e conflitos de unicidade (→ `409`). Essa é uma exceção deliberada à convenção de organização por feature — não um retrocesso para arquitetura em camadas — porque a tradução de erro para `ProblemDetail` é infraestrutura transversal exigida pelo ADR-0003 para a API inteira, não lógica de negócio de nenhum domínio específico.

### Alternativas consideradas

- **Handler local por domínio** (`auth/`, `user/`, `rbac/` cada um com seu próprio `@ExceptionHandler`): descartado por duplicar a tradução `Exception → ProblemDetail` em cada domínio, violando o próprio ADR-0003 (formato único para toda a API) e criando risco de divergência entre domínios ao longo do tempo.

## Consequências

**Positivas**

- Um único ponto de manutenção para a tradução de erro, coerente com o ADR-0003 valer para toda a API.
- Novos domínios (`user/`, `rbac/`) herdam o tratamento de erro sem precisar reimplementá-lo.

**Trade-offs aceitos**

- Introduz a primeira exceção deliberada à convenção de organização por feature — precisa ficar claro (este ADR) que é infraestrutura transversal, não o início de uma volta a camadas técnicas globais.
- Se o projeto crescer e domínios precisarem de tratamento de erro muito divergente entre si, esse pacote compartilhado pode precisar ser revisitado.
