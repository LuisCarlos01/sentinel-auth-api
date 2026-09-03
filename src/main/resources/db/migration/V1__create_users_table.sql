-- gen_random_uuid() é nativa do PostgreSQL a partir da versão 13 (incorporada ao core;
-- antes disso, exigia a extensão pgcrypto). O projeto roda em postgres:17-alpine
-- (ver docker-compose.yml), então nenhuma extensão adicional é necessária aqui.
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          TEXT NOT NULL,
    password_hash  TEXT NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    locked         BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unicidade case-insensitive de email: índice funcional sobre LOWER(email) em vez
-- de UNIQUE simples na coluna, para que "Jane@Example.com" e "jane@example.com"
-- sejam tratados como o mesmo email. Evitamos a extensão citext propositalmente,
-- pois ela mudaria o tipo da coluna e poderia gerar atrito com o ddl-auto: validate
-- do Hibernate (TEXT puro já foi validado como livre de fricção).
CREATE UNIQUE INDEX users_email_unique_idx ON users (LOWER(email));
