package dev.sentinel.auth.common.web;

import dev.sentinel.auth.auth.EmailAlreadyRegisteredException;
import dev.sentinel.auth.auth.InvalidCredentialsException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento de erro transversal, no formato RFC 9457 ("Problem Details for HTTP APIs"), via
 * {@link ProblemDetail} nativo do Spring — ver ADR-0007 para o raciocínio de centralizar esse
 * tratamento neste pacote técnico compartilhado, exceção deliberada à organização por feature.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Email already registered");
        return problemDetail;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Invalid credentials");
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorEntry)
                .toList();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problemDetail.setTitle("Invalid request content");
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    private Map<String, String> toErrorEntry(FieldError fieldError) {
        // Map.of lança NPE em valor nulo; getDefaultMessage() pode retornar null se a anotação de
        // validação não tiver mensagem configurada.
        String message = Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Invalid value");
        return Map.of("field", fieldError.getField(), "message", message);
    }
}
