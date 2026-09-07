package dev.sentinel.auth.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limita tentativas de {@code POST /api/v1/auth/login} — único endpoint com o threat model de
 * brute force/credential stuffing documentado (`docs/security-threats.md`), ver ADR-0010. Dois
 * buckets independentes por requisição (IP do cliente e e-mail do corpo), 5 tentativas/minuto
 * cada, ambos precisando ter capacidade disponível. Em memória (Caffeine, eviction automática) —
 * sem backend distribuído, coerente com o projeto rodar como instância única.
 *
 * <p>Excedido qualquer um dos dois limites, a resposta é {@code 429} em RFC 9457, com mensagem
 * genérica — não revela qual dos dois buckets estourou, mesma filosofia do erro de credenciais
 * inválidas já existente. O {@code ProblemDetail} é escrito direto na resposta, fora do
 * {@code @RestControllerAdvice} compartilhado (`common/web`, ADR-0007) — mesmo motivo estrutural
 * de {@link RestAuthenticationEntryPoint}/{@link RestAccessDeniedHandler}: a cadeia de filtros do
 * Spring Security roda antes do {@code DispatcherServlet}.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LOGIN_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login");

    private final JsonMapper jsonMapper;
    private final int capacity;
    private final Duration refillWindow;
    private final Cache<String, Bucket> ipBuckets;
    private final Cache<String, Bucket> emailBuckets;

    public LoginRateLimitFilter(JsonMapper jsonMapper, int capacity, Duration refillWindow) {
        this.jsonMapper = jsonMapper;
        this.capacity = capacity;
        this.refillWindow = refillWindow;
        this.ipBuckets = newBucketCache(refillWindow);
        this.emailBuckets = newBucketCache(refillWindow);
    }

    // expireAfterAccess com folga sobre a janela de refill: uma chave parada não cresce o cache
    // indefinidamente, mas também não expira no meio de uma janela ainda ativa.
    private static Cache<String, Bucket> newBucketCache(Duration refillWindow) {
        return Caffeine.newBuilder()
                .expireAfterAccess(refillWindow.multipliedBy(2))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request, body);

        Bucket ipBucket = ipBuckets.get(request.getRemoteAddr(), key -> newBucket());
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);

        String email = extractEmail(body);
        ConsumptionProbe emailProbe = null;
        if (email != null) {
            Bucket emailBucket = emailBuckets.get(email, key -> newBucket());
            emailProbe = emailBucket.tryConsumeAndReturnRemaining(1);
        }

        if (!ipProbe.isConsumed() || (emailProbe != null && !emailProbe.isConsumed())) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillWindow))
                .build();
    }

    // Corpo malformado/sem "email": trata como ausente — o bucket de IP sozinho ainda se aplica,
    // e a validação de Bean Validation do LoginRequest cuida do corpo inválido normalmente.
    private String extractEmail(byte[] body) {
        try {
            return jsonMapper.readValue(body, LoginEmailOnly.class).email();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
        problemDetail.setTitle("Too Many Requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), problemDetail);
    }

    private record LoginEmailOnly(String email) {}

    // O corpo já foi consumido para extrair o e-mail — este wrapper permite que o
    // DispatcherServlet/Jackson leiam o mesmo corpo de novo, mais adiante na cadeia.
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}

                @Override
                public int read() {
                    return inputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
