package dev.sentinel.auth.rbac;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data JPA para persistência de {@link Role}.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);
}
