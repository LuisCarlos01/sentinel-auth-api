package dev.sentinel.auth.auth;

/**
 * Sinaliza uma falha de autenticação em {@code POST /api/v1/auth/login} — email inexistente,
 * senha incorreta, ou conta {@code locked}/{@code enabled=false}.
 *
 * <p>Mensagem deliberadamente genérica e única para todos os casos: o objetivo é que a resposta
 * {@code 401} não permita a um atacante distinguir entre "conta não existe" e "conta existe mas
 * está bloqueada/errada" (ver spec de login).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
