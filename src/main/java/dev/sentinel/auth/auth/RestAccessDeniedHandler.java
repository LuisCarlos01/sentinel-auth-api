package dev.sentinel.auth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz falha de autorização (usuário autenticado sem o papel exigido pela rota) para RFC 9457
 * (ADR-0003), mesmo formato dos demais erros da API — mas fora do {@code @RestControllerAdvice}
 * compartilhado (`common/web`, ADR-0007), pelo mesmo motivo estrutural de
 * {@link RestAuthenticationEntryPoint}: a cadeia de filtros do Spring Security roda antes do
 * {@code DispatcherServlet}.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Missing required role");
        problemDetail.setTitle("Forbidden");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), problemDetail);
    }
}
