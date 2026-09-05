package dev.sentinel.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/auth/register}.
 *
 * <p>Validado via Jakarta Bean Validation (ADR-0004) — violações são traduzidas para
 * {@code 400 Bad Request} no formato RFC 9457 pelo {@code GlobalExceptionHandler} compartilhado
 * (ADR-0007).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8) String password) {}
