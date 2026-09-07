package dev.sentinel.auth.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta do {@link LoginRateLimitFilter} (ticket #19, ADR-0010).
 * Único teste da suíte que sobrescreve {@code sentinel.rate-limit.login-*} de volta para o valor
 * real de produção (5/60s) — o profile {@code test} usa uma capacidade generosa por padrão
 * (`application-test.yml`) para não afetar outras classes que chamam {@code login} várias vezes
 * sem a intenção de testar rate limiting. Cada método usa um IP/e-mail exclusivo, nunca
 * reaproveitado por outro teste da classe — os buckets vivem no filtro (um só por contexto
 * Spring, compartilhado entre os métodos de teste), não são resetados pelo rollback transacional
 * do banco. Ver {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class LoginRateLimitFilterTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("sentinel.rate-limit.login-capacity", () -> 5);
        registry.add("sentinel.rate-limit.login-window-seconds", () -> 60);
    }

    @Test
    void sixthAttemptInSameMinuteFromSameIpAndEmailIsRateLimited() throws Exception {
        String ip = "10.0.0.1";
        String email = "same-ip-and-email@example.com";

        for (int i = 0; i < 5; i++) {
            performLogin(ip, email).andExpect(status().isUnauthorized());
        }

        performLogin(ip, email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void emailBucketAppliesAcrossDifferentIps() throws Exception {
        String email = "same-email-different-ips@example.com";

        for (int i = 0; i < 5; i++) {
            performLogin("10.0.1." + i, email).andExpect(status().isUnauthorized());
        }

        performLogin("10.0.1.99", email).andExpect(status().isTooManyRequests());
    }

    @Test
    void ipBucketAppliesAcrossDifferentEmails() throws Exception {
        String ip = "10.0.2.1";

        for (int i = 0; i < 5; i++) {
            performLogin(ip, "same-ip-different-emails-" + i + "@example.com")
                    .andExpect(status().isUnauthorized());
        }

        performLogin(ip, "same-ip-different-emails-99@example.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void attemptsWithinLimitAreNotRateLimited() throws Exception {
        String ip = "10.0.3.1";
        String email = "within-limit@example.com";

        for (int i = 0; i < 3; i++) {
            performLogin(ip, email).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions performLogin(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(email, "Str0ngP@ssw0rd!"))));
    }
}
