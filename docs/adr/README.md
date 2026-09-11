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
| [0005](0005-defer-rate-limiting.md) | Rate limiting adiado para a fase de Quality & Security | Accepted |
| [0006](0006-manual-roadmap-sync-automation.md) | Automação de sincronização do roadmap manual e aprovada por humano | Accepted |
| [0007](0007-shared-error-handling-package.md) | Pacote técnico compartilhado para tratamento de erro RFC 9457 | Accepted |
| [0008](0008-opaque-hashed-single-use-refresh-token.md) | Refresh token opaco, hasheado e de uso único | Accepted |
| [0009](0009-dual-channel-refresh-token-delivery.md) | Entrega do refresh token por corpo JSON e cookie simultaneamente | Accepted |
| [0010](0010-in-memory-rate-limiting-on-login.md) | Rate limiting em memória no login, via Bucket4j | Accepted |
| [0011](0011-aws-ec2-manual-deploy.md) | Deploy do v1.0.0 em EC2 (AWS), manual, sem RDS/HTTPS/domínio | Accepted |
| [0012](0012-cors-configuration.md) | Configuração de CORS para clientes browser | Accepted |
| [0013](0013-https-via-elastic-ip-sslip-caddy.md) | HTTPS via Elastic IP + sslip.io + reverse proxy (Caddy) com Let's Encrypt | Accepted |
| [0014](0014-cross-origin-refresh-token-via-body.md) | Cliente web usa o corpo JSON (não o cookie) para refresh em topologia cross-origin | Accepted |
