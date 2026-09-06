package dev.sentinel.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta cobrindo o registro de email duplicado (case-insensitive) em
 * {@code POST /api/v1/auth/register}. Ver {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class AuthControllerRegisterDuplicateEmailTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void rejectsSecondRegistrationWithSameEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("jane.doe@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void rejectsRegistrationWithSameEmailDifferentCase() throws Exception {
        RegisterRequest first = new RegisterRequest("Jane@Example.com", "Str0ngP@ssw0rd!");
        RegisterRequest second = new RegisterRequest("jane@example.com", "An0therP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    /**
     * Duas requisições com o mesmo email, sincronizadas via {@link CyclicBarrier} para disparar
     * no mesmo instante, sem mock de nenhum colaborador — a constraint de unicidade real do
     * Postgres decide qual das duas falha. Isso prova a garantia observável (uma responde 201, a
     * outra 409), mas NÃO garante qual dos dois caminhos de {@code AuthService.register} tratou o
     * conflito: o {@code CyclicBarrier} só sincroniza o instante antes do {@code mockMvc.perform},
     * não a consulta ao banco em si — em execuções mais lentas o `existsByEmail` (caminho rápido)
     * pode interceptar a segunda thread antes que o `catch (DataIntegrityViolationException)`
     * (rede de segurança) entre em jogo. Não afirme certeza sobre qual branch foi exercitado sem
     * instrumentar o teste para isso.
     */
    @Test
    void enforcesUniqueEmailUnderConcurrentRegistrations() throws Exception {
        String email = "concurrent.race@example.com";
        RegisterRequest first = new RegisterRequest(email, "Str0ngP@ssw0rd!");
        RegisterRequest second = new RegisterRequest(email, "An0therP@ssw0rd!");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Integer>> futures =
                    executor.invokeAll(List.of(() -> performRegister(barrier, first), () -> performRegister(barrier, second)));

            List<Integer> statuses = futures.stream().map(this::resolve).toList();

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdown();
        }
    }

    private int performRegister(CyclicBarrier barrier, RegisterRequest request) throws Exception {
        barrier.await();
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int resolve(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
