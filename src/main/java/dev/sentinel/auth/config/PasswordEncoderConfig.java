package dev.sentinel.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuração transversal de encoding de senha.
 * Expõe explicitamente o {@link Argon2PasswordEncoder} (Argon2id, parâmetros default do
 * Spring Security) como único {@link PasswordEncoder} do contexto — evita
 * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}, cujo algoritmo
 * default é bcrypt, conforme decisão registrada em docs/security-threats.md.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
