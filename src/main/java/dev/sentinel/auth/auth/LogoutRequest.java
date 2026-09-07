package dev.sentinel.auth.auth;

/**
 * Corpo de {@code POST /api/v1/auth/logout}. Sem anotação de Bean Validation, mesma exceção
 * deliberada de {@link RefreshRequest} (ADR-0004/ADR-0009): um valor ausente/vazio é tratado como
 * Refresh token inválido (mesmo {@code 401} genérico), não como erro de validação {@code 400}.
 */
public record LogoutRequest(String refreshToken) {}
