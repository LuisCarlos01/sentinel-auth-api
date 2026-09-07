package dev.sentinel.auth.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Corpo de cada item de {@code GET /api/v1/users}. Tipo próprio — não reaproveita
 * {@code RegisterResponse} do domínio {@code auth}, mesmo com shape parecido: operações
 * semanticamente diferentes (listar usuários vs. confirmar um registro).
 */
public record UserSummaryResponse(UUID id, String email, Instant createdAt) {}
