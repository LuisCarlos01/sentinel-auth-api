package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração de {@link RefreshTokenCleanupJob}, chamando {@code cleanUpExpiredTokens()}
 * diretamente — não espera o agendador do Spring disparar (ver Javadoc da classe). Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class RefreshTokenCleanupJobTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RefreshTokenCleanupJob refreshTokenCleanupJob;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void removesOnlyExpiredRefreshTokens() {
        User user = userRepository.saveAndFlush(new User("cleanup@example.com", "argon2-hash-placeholder"));

        RefreshToken expired = refreshTokenRepository.save(
                new RefreshToken(user, "expired-token-hash", Instant.now().minusSeconds(60)));
        RefreshToken valid = refreshTokenRepository.save(
                new RefreshToken(user, "valid-token-hash", Instant.now().plusSeconds(3600)));

        refreshTokenCleanupJob.cleanUpExpiredTokens();

        List<RefreshToken> remaining = refreshTokenRepository.findAll();
        assertThat(remaining).extracting(RefreshToken::getId).containsExactly(valid.getId());
        assertThat(remaining).extracting(RefreshToken::getId).doesNotContain(expired.getId());
    }
}
