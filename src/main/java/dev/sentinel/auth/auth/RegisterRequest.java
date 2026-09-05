package dev.sentinel.auth.auth;

/**
 * Corpo de {@code POST /api/v1/auth/register}.
 *
 * <p>Sem anotações de Bean Validation aqui de propósito — pertencem ao ticket de validação de
 * entrada (400 RFC 9457), rodando em paralelo a este.
 */
public record RegisterRequest(String email, String password) {}
