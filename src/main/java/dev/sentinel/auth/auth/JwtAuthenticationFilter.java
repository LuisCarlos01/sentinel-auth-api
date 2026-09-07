package dev.sentinel.auth.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Popula o {@link SecurityContextHolder} a partir do Access token (JWT) no header
 * {@code Authorization: Bearer}, confiando inteiramente nos claims — sem consultar o banco a
 * cada requisição, coerente com o TTL curto (15 min) do Access token (decisão da grilling do
 * ciclo refresh/logout: statelessness prevalece sobre revogação imediata de conta bloqueada).
 *
 * <p>Um token ausente, malformado ou expirado <b>não</b> rejeita a requisição aqui — a cadeia
 * segue sem autenticar, e só vira {@code 401} se a rota exigir autenticação (via
 * {@link RestAuthenticationEntryPoint}). Rotas públicas continuam funcionando normalmente mesmo
 * com um header inválido.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtService.parseClaims(header.substring(BEARER_PREFIX.length()));
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(claims));
            } catch (RuntimeException ex) {
                // Assinatura inválida, expirado, malformado, etc. — segue sem autenticar (Javadoc
                // da classe).
            }
        }
        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken toAuthentication(Claims claims) {
        List<String> roles = claims.get(JwtService.ROLES_CLAIM, List.class);
        List<GrantedAuthority> authorities =
                roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }
}
