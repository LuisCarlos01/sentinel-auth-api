package dev.sentinel.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Teste unitário do algoritmo de encoding de senha usado por {@link PasswordEncoderConfig}.
 * Instancia o {@link Argon2PasswordEncoder} diretamente (sem subir contexto Spring) para
 * validar o comportamento de encode/matches isoladamente.
 */
class PasswordEncoderConfigTest {

    private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Test
    void encodeGeneratesVerifiableHash() {
        String rawPassword = "Str0ngP@ssw0rd!";

        String hash = passwordEncoder.encode(rawPassword);

        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, hash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForIncorrectPassword() {
        String rawPassword = "Str0ngP@ssw0rd!";
        String wrongPassword = "SomeOtherPassword1!";

        String hash = passwordEncoder.encode(rawPassword);

        assertThat(passwordEncoder.matches(wrongPassword, hash)).isFalse();
    }
}
