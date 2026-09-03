package dev.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SentinelAuthApiApplicationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        // apenas valida que o contexto Spring sobe com um Postgres real via Testcontainers
    }

    @Test
    void exposesExactlyOnePasswordEncoderBean() {
        Map<String, PasswordEncoder> passwordEncoderBeans = applicationContext.getBeansOfType(PasswordEncoder.class);

        assertThat(passwordEncoderBeans).hasSize(1);
    }

    @Test
    void passwordEncoderBeanEncodesAndVerifiesPasswordEndToEnd() {
        // Exercita o PasswordEncoder real, injetado pelo Spring, chamando encode()/matches().
        // Se org.bouncycastle:bcprov-jdk18on for removido do classpath de runtime, o
        // Argon2PasswordEncoder gerenciado pelo Spring falha aqui com NoClassDefFoundError,
        // mesmo que o contexto suba normalmente (ver contextLoads()).
        String rawPassword = "Str0ngP@ssw0rd!";
        String wrongPassword = "SomeOtherPassword1!";

        String hash = passwordEncoder.encode(rawPassword);

        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, hash)).isTrue();
        assertThat(passwordEncoder.matches(wrongPassword, hash)).isFalse();
    }
}
