package dev.sentinel.auth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Constrói, assina e decodifica o Access token (JWT, HS256). Componente próprio, separado de
 * {@link AuthService}, para que o futuro {@code refresh} e o filtro de segurança reaproveitem a
 * mesma lógica de emissão/leitura sem duplicá-la (ver spec de login).
 */
@Service
public class JwtService {

    static final String ROLES_CLAIM = "roles";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${sentinel.jwt.signing-key}") String signingKey,
            @Value("${sentinel.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
        // Chave vinda de variável de ambiente (sentinel.jwt.signing-key -> JWT_SIGNING_KEY),
        // nunca hardcoded nem commitada como segredo real — ver application.yml.
        this.signingKey = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    /**
     * Gera um Access token assinado com claim {@code sub} igual ao id do usuário e claim
     * {@code roles} com os nomes das roles, sem o prefixo {@code ROLE_} (esse prefixo é uma
     * conversão de infraestrutura do Spring Security, não um conceito de domínio — ver CONTEXT.md).
     */
    public String generateAccessToken(UUID userId, Set<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** TTL do Access token, usado para derivar o {@code expiresIn} (em segundos) da resposta. */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /** Decodifica e valida a assinatura de um Access token, retornando seus claims. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
