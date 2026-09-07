package dev.sentinel.auth.user;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Orquestra operações sobre {@link User}. Hoje só a listagem (`GET /api/v1/users`, restrita a
 * `ADMIN`) — sem paginação/filtro (YAGNI frente à escala documentada do projeto).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSummaryResponse> listAll() {
        return userRepository.findAll().stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getEmail(), user.getCreatedAt()))
                .toList();
    }
}
