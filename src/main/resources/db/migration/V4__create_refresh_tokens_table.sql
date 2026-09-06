-- Refresh tokens são opacos e hasheados com SHA-256 antes de persistir
-- (ADR-0008) — a coluna token_hash nunca contém o valor em texto plano.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Suporta o lookup por hash que o futuro fluxo de refresh/logout vai precisar
-- (ADR-0008 — validação por lookup direto, sem auto-validação de JWT).
CREATE UNIQUE INDEX refresh_tokens_token_hash_unique_idx ON refresh_tokens (token_hash);
