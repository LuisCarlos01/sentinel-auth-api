# 0008 — Refresh token opaco, hasheado e de uso único

## Status

Accepted

## Contexto

`docs/technologies/jjwt.md` sinalizava como ponto em aberto se o refresh token seria um JWT assinado (com `jti` referenciado na tabela de persistência) ou um token opaco validado por lookup direto no banco. Como o refresh token já precisa ser consultado no banco a cada uso para permitir revogação real (decisão já registrada em `docs/architecture.md`), a auto-validação de um JWT — sua principal vantagem sobre um token opaco — não se aplica aqui: o banco já é consultado de qualquer forma.

Além do formato, duas questões de segurança e ciclo de vida precisavam de decisão: se o valor persistido deveria ser hasheado (um refresh token vazado do banco é uma credencial de bearer utilizável imediatamente), e o que acontece com a linha da tabela quando o token é usado (rotação) ou revogado (`logout`).

## Decisão

O refresh token é um **valor opaco aleatório** (não um JWT), **hasheado com SHA-256** antes de ser persistido em `refresh_tokens` (comparação por lookup do hash, nunca do valor em texto plano). A linha correspondente é **deletada** tanto na rotação (uso do token no `refresh`) quanto na revogação (`logout`) — a tabela contém, a qualquer momento, apenas tokens atualmente válidos; "inválido" e "não encontrado" são a mesma coisa.

Isso significa que o projeto **não implementa detecção de reuso de refresh token roubado** (um recurso mais avançado, presente em alguns sistemas de produção, que mantém histórico para identificar quando um token já consumido é reapresentado). Decisão consciente de escopo, não uma omissão silenciosa — pode ser revisitada na fase `v0.5.0 — Quality & Security` se fizer sentido.

### Alternativas consideradas

- **JWT assinado com `jti`**: descartado porque a vantagem de auto-validação sem consulta ao banco desaparece quando a revogação real já exige esse lookup a cada uso.
- **Hashing via Argon2id** (mesmo algoritmo da senha): descartado — Argon2id é lento/memory-hard de propósito, para dificultar brute-force de senhas de baixa entropia digitadas por humanos. Um refresh token já nasce aleatório e de alta entropia; o custo computacional do Argon2id nesse caso só penaliza performance sem ganho real de segurança. SHA-256 é suficiente e mantém o lookup rápido.
- **Manter histórico de tokens revogados** (soft-delete via `revoked_at`): descartado por YAGNI — nenhum requisito de auditoria está registrado em nenhum documento do projeto até o momento.

## Consequências

**Positivas**

- Schema simples: uma tabela cujo conteúdo é sempre "o que é válido agora", sem estados intermediários pra raciocinar.
- Sem chave de assinatura JWT adicional a gerenciar só para o refresh token.
- Hashing rápido (SHA-256) mantém o custo de cada `login`/`refresh` baixo, sem repetir o custo do Argon2id onde ele não agrega segurança.

**Trade-offs aceitos**

- Sem detecção de reuso de token roubado: se um refresh token vazar e for usado por um atacante antes do dono legítimo, o sistema não consegue distinguir esse caso de um uso normal nem revogar a sessão inteira preventivamente.
- Sem histórico de tokens para auditoria/forense caso um incidente de segurança precise ser investigado retroativamente.
