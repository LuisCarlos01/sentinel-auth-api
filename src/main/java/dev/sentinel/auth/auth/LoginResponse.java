package dev.sentinel.auth.auth;

/**
 * Corpo de resposta de sucesso de {@code POST /api/v1/auth/login}.
 *
 * @param accessToken JWT assinado (HS256), stateless, curta duração.
 * @param refreshToken valor opaco em texto plano — existe apenas nesta resposta; o que é
 *     persistido em {@code refresh_tokens} é o hash SHA-256 dele (ADR-0008).
 * @param tokenType sempre {@code "Bearer"}.
 * @param expiresIn TTL do access token em segundos.
 */
public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
