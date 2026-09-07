package dev.sentinel.auth.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data JPA para persistência de {@link RefreshToken}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByExpiresAtBefore(Instant instant);
}
