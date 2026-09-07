package dev.sentinel.auth.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.auth.AbstractAuthIntegrationTest;
import dev.sentinel.auth.auth.LoginRequest;
import dev.sentinel.auth.auth.LoginResponse;
import dev.sentinel.auth.auth.RegisterRequest;
import dev.sentinel.auth.rbac.Role;
import dev.sentinel.auth.rbac.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code GET /api/v1/users}, o recurso que demonstra o RBAC
 * enxuto (ADR-0001) já modelado desde a v0.2.0/v0.3.0. Ver {@link AbstractAuthIntegrationTest}
 * para o setup comum.
 */
class UserControllerListUsersTest extends AbstractAuthIntegrationTest {

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
    private RoleRepository roleRepository;

    @Test
    void adminListsAllUsers() throws Exception {
        String email = "admin@example.com";
        LoginResponse loginResponse = registerAsAdminAndLogin(email, "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$[0].createdAt", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void nonAdminUserIsForbidden() throws Exception {
        LoginResponse loginResponse = registerAndLogin("plain-user@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    // Concede ADMIN antes do login (não depois) para que o access token emitido já carregue a
    // claim "roles" atualizada — o filtro JWT confia só nos claims, sem consultar o banco.
    private LoginResponse registerAsAdminAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest(email, password))));

        User user = userRepository.findByEmail(email).orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        user.addRole(adminRole);
        userRepository.saveAndFlush(user);

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(responseBody, LoginResponse.class);
    }
}
