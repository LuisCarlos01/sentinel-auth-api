package dev.sentinel.auth.auth;

/**
 * Sinaliza um Refresh token inexistente, expirado, já usado (Rotação já consumida), ou ausente
 * em {@code POST /api/v1/auth/refresh} — todos os casos respondem com o mesmo {@code 401}
 * genérico, sem distinguir a causa (ver spec do ciclo refresh/logout).
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid, expired, or already used refresh token");
    }
}
