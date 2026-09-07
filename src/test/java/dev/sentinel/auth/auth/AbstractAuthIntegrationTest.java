package dev.sentinel.auth.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base comum dos testes de integração ponta a ponta de {@code auth}: contexto Spring real +
 * PostgreSQL real via Testcontainers, sem mockar nenhum colaborador interno.
 *
 * <p>O container e o {@code @DynamicPropertySource} continuam declarados em cada subclasse
 * (não aqui) — uma tentativa de compartilhar um único container estático entre as subclasses
 * causou recriação inesperada do container em meio à suíte (duas portas diferentes na mesma
 * execução), quebrando o pool de conexões do Hikari. Cada classe mantém seu próprio container,
 * como antes; esta base só remove a duplicação das anotações e do wiring de {@code MockMvc}/
 * {@code JsonMapper}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
abstract class AbstractAuthIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Registra e loga um usuário novo, retornando o par de tokens emitido pelo login. */
    protected LoginResponse registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest(email, password))));

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(responseBody, LoginResponse.class);
    }
}
