package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Cobre o critério de aceite da issue #4 que exige o fluxo completo
 * {@code register → login → refresh → logout} ponta a ponta contra um Postgres real. Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class AuthFullFlowTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void completesRegisterLoginRefreshLogoutFlow() throws Exception {
        String email = "full-flow@example.com";
        String password = "Str0ngP@ssw0rd!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegisterRequest(email, password))))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        LoginResponse loginResponse = jsonMapper.readValue(loginBody, LoginResponse.class);

        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(loginResponse.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        LoginResponse refreshResponse = jsonMapper.readValue(refreshBody, LoginResponse.class);
        assertThat(refreshResponse.refreshToken()).isNotEqualTo(loginResponse.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshResponse.accessToken())
                        .cookie(new Cookie("refreshToken", refreshResponse.refreshToken())))
                .andExpect(status().isNoContent());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(refreshTokenRepository.findAll())
                .noneMatch(token -> token.getUser().getId().equals(user.getId()));
    }
}
