package dev.sentinel.auth.auth;

/**
 * Corpo de {@code POST /api/v1/auth/refresh}. Sem anotação de Bean Validation (ADR-0004):
 * exceção deliberada registrada em ADR-0009 — um valor ausente/vazio é tratado como Refresh
 * token inválido (mesmo {@code 401} genérico de {@link InvalidRefreshTokenException}), não como
 * erro de validação {@code 400}.
 */
public record RefreshRequest(String refreshToken) {}
