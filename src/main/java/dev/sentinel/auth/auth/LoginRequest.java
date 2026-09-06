package dev.sentinel.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/auth/login}.
 *
 * <p>DTO próprio, sem tipo compartilhado com {@link RegisterRequest} mesmo com forma de wire
 * parecida — Registro e Login são operações semanticamente diferentes (ver spec).
 *
 * <p>Validado via Jakarta Bean Validation (ADR-0004) — violações são traduzidas para
 * {@code 400 Bad Request} no formato RFC 9457 pelo {@code GlobalExceptionHandler} compartilhado
 * (ADR-0007), reaproveitando o mesmo handler já usado pelo {@code register}. O {@code @Size} de
 * senha roda antes de qualquer consulta ao banco — não vaza nada sobre uma conta específica
 * (existente ou não), diferente da resposta de erro de credencial (essa sim genérica, via
 * {@link InvalidCredentialsException}). Mantido simétrico ao de {@link RegisterRequest}.
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {}
