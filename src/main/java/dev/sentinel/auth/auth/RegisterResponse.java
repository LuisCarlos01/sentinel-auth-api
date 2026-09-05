package dev.sentinel.auth.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Corpo de resposta de sucesso de {@code POST /api/v1/auth/register}.
 * Nunca inclui senha/hash nem token — Registro não autentica.
 */
public record RegisterResponse(UUID id, String email, Instant createdAt) {}
