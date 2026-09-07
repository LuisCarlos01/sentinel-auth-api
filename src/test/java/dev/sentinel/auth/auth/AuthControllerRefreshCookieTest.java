package dev.sentinel.auth.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta da entrega dual-channel do Refresh token via cookie
 * (ADR-0009, ticket 02) — cobre a escrita do cookie em {@code login}/{@code refresh} e a leitura
 * com precedência sobre o corpo em {@code refresh}. Ver {@link AbstractAuthIntegrationTest} para
 * o setup comum.
 */
class AuthControllerRefreshCookieTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void loginSetsRefreshTokenCookieWithExpectedAttributes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest("cookie@example.com", "Str0ngP@ssw0rd!"))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new LoginRequest("cookie@example.com", "Str0ngP@ssw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refreshToken", 7 * 24 * 3600))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.refreshToken", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void refreshSucceedsUsingOnlyCookie() throws Exception {
        LoginResponse loginResponse = registerAndLogin("cookie-only@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", loginResponse.refreshToken()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void refreshPrefersCookieOverBodyWhenBothPresent() throws Exception {
        LoginResponse loginResponse = registerAndLogin("cookie-precedence@example.com", "Str0ngP@ssw0rd!");

        // O corpo traz um valor inválido; se o corpo fosse usado em vez do cookie, a resposta
        // seria 401. O 200 abaixo prova que o cookie (válido) foi o valor efetivamente usado.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", loginResponse.refreshToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest("not-the-real-token"))))
                .andExpect(status().isOk());
    }
}
