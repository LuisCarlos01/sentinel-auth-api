package dev.sentinel.auth.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositório Spring Data JPA para persistência de {@link User}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    // Comparação explícita case-insensitive, como defesa adicional em relação
    // à normalização feita na escrita (não depende apenas dela).
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
}
