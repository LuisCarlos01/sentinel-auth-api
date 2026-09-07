package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import io.jsonwebtoken.Claims;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/auth/refresh}, cobrindo o canal de
 * corpo JSON (ticket 01 — o cookie de ADR-0009 é escopo de um ciclo seguinte). Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class AuthControllerRefreshTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rotatesRefreshTokenAndIssuesNewTokenPair() throws Exception {
        String email = "rotate@example.com";
        LoginResponse loginResponse = registerAndLogin(email, "Str0ngP@ssw0rd!");

        String responseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(loginResponse.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponse refreshResponse = jsonMapper.readValue(responseBody, LoginResponse.class);
        assertThat(refreshResponse.refreshToken()).isNotEqualTo(loginResponse.refreshToken());

        User persistedUser = userRepository.findByEmail(email).orElseThrow();
        Claims claims = jwtService.parseClaims(refreshResponse.accessToken());
        assertThat(claims.getSubject()).isEqualTo(persistedUser.getId().toString());
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactly("USER");

        List<RefreshToken> persisted = refreshTokenRepository.findAll();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getTokenHash()).isEqualTo(sha256Hex(refreshResponse.refreshToken()));
    }

    @Test
    void respondsUnauthorizedForNonexistentRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest("never-issued-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void respondsUnauthorizedForExpiredRefreshToken() throws Exception {
        String email = "expired@example.com";
        registerUser(email, "Str0ngP@ssw0rd!");
        User user = userRepository.findByEmail(email).orElseThrow();

        String rawExpiredToken = "expired-raw-token";
        refreshTokenRepository.save(
                new RefreshToken(user, sha256Hex(rawExpiredToken), Instant.now().minusSeconds(1)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(rawExpiredToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void respondsUnauthorizedWhenRefreshTokenAlreadyUsed() throws Exception {
        LoginResponse loginResponse = registerAndLogin("already-used@example.com", "Str0ngP@ssw0rd!");
        String rawRefreshToken = loginResponse.refreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(rawRefreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(rawRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void respondsUnauthorizedWhenRefreshTokenMissingFromBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RefreshRequest(null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest(email, password))));
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes()));
    }
}
