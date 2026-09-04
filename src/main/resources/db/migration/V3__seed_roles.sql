-- Papéis padrão do RBAC enxuto (ver ADR-0001). Nomes sem prefixo "ROLE_" —
-- a conversão para GrantedAuthority é responsabilidade da camada de auth,
-- não deste schema.
INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');
