package dev.sentinel.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração de {@link UserRepository} contra um PostgreSQL real via Testcontainers,
 * seguindo o mesmo padrão já estabelecido em {@code SentinelAuthApiApplicationTests}.
 *
 * <p>{@code @Transactional} garante rollback automático ao final de cada teste, isolando os
 * dados entre execuções em vez de depender de emails distintos por teste.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class UserRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsAndFindsUserByEmail() {
        String email = "jane.doe@example.com";
        // Hash de exemplo — nunca a senha em texto plano é persistida ou comparada aqui.
        String passwordHash = "argon2-hash-placeholder";
        User user = new User(email, passwordHash);

        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByEmail(email);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
        assertThat(found.get().getPasswordHash()).isEqualTo(passwordHash);
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().isEnabled()).isTrue();
        assertThat(found.get().isLocked()).isFalse();
        assertThat(found.get().isEmailVerified()).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        String duplicateEmail = "duplicate@example.com";
        userRepository.saveAndFlush(new User(duplicateEmail, "hash-one"));

        User secondUser = new User(duplicateEmail, "hash-two");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(secondUser));
    }

    @Test
    void rejectsDuplicateEmailWithDifferentCase() {
        userRepository.saveAndFlush(new User("Jane@Example.com", "hash-one"));

        User secondUser = new User("jane@example.com", "hash-two");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(secondUser));
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("Jane@Example.com", "argon2-hash-placeholder"));

        Optional<User> found = userRepository.findByEmail("JANE@EXAMPLE.COM");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void normalizesEmailToLowercaseOnConstruction() {
        User user = new User("Mixed.Case@Example.COM", "argon2-hash-placeholder");

        assertThat(user.getEmail()).isEqualTo("mixed.case@example.com");
    }
}
