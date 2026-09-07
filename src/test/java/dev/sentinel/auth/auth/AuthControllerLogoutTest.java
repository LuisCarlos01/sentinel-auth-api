package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/auth/logout} e do
 * {@code JwtAuthenticationFilter}/{@code RestAuthenticationEntryPoint} que o sustentam (ticket
 * 03). Ver {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class AuthControllerLogoutTest extends AbstractAuthIntegrationTest {

    // Mesma chave fixa de application-test.yml — usada aqui só para forjar um Access token já
    // expirado, cenário que o fluxo normal da API não produz em segundos.
    private static final String TEST_SIGNING_KEY = "test-only-signing-key-not-used-in-production-1234567890";

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
    void logsOutRevokingRefreshTokenAndClearingCookie() throws Exception {
        LoginResponse loginResponse = registerAndLogin("logout@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .cookie(new Cookie("refreshToken", loginResponse.refreshToken())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        User user = userRepository.findByEmail("logout@example.com").orElseThrow();
        assertThat(refreshTokenRepository.findAll())
                .noneMatch(token -> token.getUser().getId().equals(user.getId()));
    }

    @Test
    void respondsUnauthorizedWhenAccessTokenMissing() throws Exception {
        LoginResponse loginResponse = registerAndLogin("no-token@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", loginResponse.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void respondsUnauthorizedForMalformedAccessToken() throws Exception {
        LoginResponse loginResponse = registerAndLogin("malformed-token@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                        .cookie(new Cookie("refreshToken", loginResponse.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respondsUnauthorizedForExpiredAccessToken() throws Exception {
        LoginResponse loginResponse = registerAndLogin("expired-token@example.com", "Str0ngP@ssw0rd!");
        User user = userRepository.findByEmail("expired-token@example.com").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken(user.getId().toString()))
                        .cookie(new Cookie("refreshToken", loginResponse.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respondsUnauthorizedWhenRefreshTokenBelongsToAnotherUser() throws Exception {
        LoginResponse owner = registerAndLogin("owner@example.com", "Str0ngP@ssw0rd!");
        LoginResponse attacker = registerAndLogin("attacker@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + attacker.accessToken())
                        .cookie(new Cookie("refreshToken", owner.refreshToken())))
                .andExpect(status().isUnauthorized());

        User ownerUser = userRepository.findByEmail("owner@example.com").orElseThrow();
        assertThat(refreshTokenRepository.findAll())
                .anyMatch(token -> token.getUser().getId().equals(ownerUser.getId()));
    }

    @Test
    void doesNotRejectPublicRouteWithInvalidAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new RegisterRequest("public-route@example.com", "Str0ngP@ssw0rd!"))))
                .andExpect(status().isCreated());
    }

    private String expiredAccessToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
