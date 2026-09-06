package dev.sentinel.auth.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta cobrindo os 4 cenários de credenciais inválidas de
 * {@code POST /api/v1/auth/login} (ticket 02): email inexistente, senha errada, conta
 * {@code locked} e conta {@code enabled=false} — todos devem resultar na mesma resposta
 * {@code 401} genérica, sem vazar qual condição falhou. Ver {@link AbstractAuthIntegrationTest}
 * para o setup comum.
 */
class AuthControllerLoginInvalidCredentialsTest extends AbstractAuthIntegrationTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Str0ngP@ssw0rd!";

    @Test
    void rejectsLoginWithNonExistentEmail() throws Exception {
        performLogin("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        String email = "jane.doe@example.com";
        registerUser(email, PASSWORD);

        performLogin(email, "WrongP@ssw0rd!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void rejectsLoginWithLockedAccount() throws Exception {
        String email = "locked.user@example.com";
        registerUser(email, PASSWORD);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setLocked(true);
        userRepository.saveAndFlush(user);

        performLogin(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void rejectsLoginWithDisabledAccount() throws Exception {
        String email = "disabled.user@example.com";
        registerUser(email, PASSWORD);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        performLogin(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    private void registerUser(String email, String password) {
        User user = new User(email, passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);
    }

    private ResultActions performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))));
    }
}
