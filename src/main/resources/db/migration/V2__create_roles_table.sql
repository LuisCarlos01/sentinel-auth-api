CREATE TABLE roles (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE
);

-- Tabela de junção pura (par de FKs, sem atributo próprio) — ver ADR-0001
-- (RBAC enxuto). ON DELETE CASCADE em ambas: apagar um usuário ou um papel
-- remove os vínculos associados sem deixar linhas órfãs.
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
