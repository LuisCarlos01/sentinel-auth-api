# Architecture Decision Records (ADR)

Este diretório reúne os registros de decisões arquiteturais do `sentinel-auth-api`, no formato [MADR](https://adr.github.io/madr/) enxuto (título, status, contexto, decisão, consequências).

## Convenção

- Um arquivo por decisão, nomeado `NNNN-titulo-curto-em-kebab-case.md`, com numeração sequencial e zero-padding de 4 dígitos.
- Status possíveis: `Proposed`, `Accepted`, `Deprecated`, `Superseded by ADR-NNNN`.
- ADRs não são editados retroativamente para refletir mudanças de decisão — se uma decisão for revista, cria-se um novo ADR que supera o anterior (atualizando o status do antigo para `Superseded by ADR-NNNN`).
- Prosa em português; identificadores de código (entidades, campos, endpoints) em inglês.

## Índice

| ADR | Título | Status |
|---|---|---|
| [0001](0001-lean-rbac-modeling.md) | Modelagem enxuta de RBAC | Accepted |
| [0002](0002-uri-based-api-versioning.md) | Versionamento de API via URI desde o início | Accepted |
| [0003](0003-rfc9457-error-format.md) | Formato de erro padronizado via RFC 9457 | Accepted |
| [0004](0004-bean-validation-input.md) | Validação de entrada via Bean Validation | Accepted |
| [0005](0005-defer-rate-limiting.md) | Rate limiting adiado para a fase de Quality | Accepted |
