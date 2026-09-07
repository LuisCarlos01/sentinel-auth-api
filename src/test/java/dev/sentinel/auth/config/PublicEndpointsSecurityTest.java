package dev.sentinel.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sentinel.auth.auth.AbstractAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Cobre uma lacuna real encontrada ao validar o deploy do v1.0.0 (issue #22): {@code
 * SecurityConfig} não liberava {@code /actuator/health} nem o Swagger UI, então ambos caíam no
 * {@code anyRequest().authenticated()} e respondiam {@code 401} sem um Access token — quebrando
 * os itens 6 e 7 do PRD ("Actuator"/"Swagger" acessíveis). Ver {@link AbstractAuthIntegrationTest}
 * para o setup comum.
 */
class PublicEndpointsSecurityTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void actuatorHealthIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void swaggerUiIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void apiDocsArePubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
