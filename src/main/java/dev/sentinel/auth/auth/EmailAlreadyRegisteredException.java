package dev.sentinel.auth.auth;

/**
 * Sinaliza que o email informado no registro já pertence a um {@link dev.sentinel.auth.user.User}
 * existente (comparação case-insensitive).
 *
 * <p>Mensagem deliberadamente genérica, sem ecoar o email recebido: o {@code 409} em si já
 * confirma a existência da conta (comportamento pedido pela spec de registro, ao contrário do
 * {@code 401} genérico de login — ver docs/security-threats.md), então repetir o valor na
 * mensagem não agrega informação nova, só redundância.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Email already registered");
    }
}
