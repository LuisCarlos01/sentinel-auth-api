package dev.sentinel.auth.auth;

import dev.sentinel.auth.rbac.Role;
import dev.sentinel.auth.rbac.RoleRepository;
import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o fluxo de autenticação. Acessa {@link UserRepository} e {@link RoleRepository}
 * diretamente — sem camada de serviço intermediária em {@code user}/{@code rbac}, seguindo as
 * camadas simples do projeto (Controller → Service → Repository).
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        Role defaultRole = roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Required role not seeded: " + DEFAULT_ROLE));

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.addRole(defaultRole);

        // saveAndFlush (não save): @CreationTimestamp só é preenchido no flush/insert, e a
        // resposta precisa do valor real de createdAt antes do fim da transação.
        User saved = userRepository.saveAndFlush(user);

        return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getCreatedAt());
    }
}
