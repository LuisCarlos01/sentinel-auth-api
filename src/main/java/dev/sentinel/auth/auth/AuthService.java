package dev.sentinel.auth.auth;

import dev.sentinel.auth.rbac.Role;
import dev.sentinel.auth.rbac.RoleRepository;
import dev.sentinel.auth.user.User;
import dev.sentinel.auth.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o fluxo de autenticação. Acessa {@link UserRepository}, {@link RoleRepository} e
 * {@link RefreshTokenRepository} diretamente — sem camada de serviço intermediária em
 * {@code user}/{@code rbac}, seguindo as camadas simples do projeto (Controller → Service →
 * Repository).
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    // 32 bytes = 256 bits de entropia, codificados em Base64 URL-safe sem padding.
    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${sentinel.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    /** TTL do Refresh token, usado por {@code AuthController} para o {@code Max-Age} do cookie (ADR-0009). */
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Caminho rápido do caso comum: evita ida ao banco para inserir e só então descobrir o conflito.
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        Role defaultRole = roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Required role not seeded: " + DEFAULT_ROLE));

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.addRole(defaultRole);

        try {
            // saveAndFlush (não save): @CreationTimestamp só é preenchido no flush/insert, e a
            // resposta precisa do valor real de createdAt antes do fim da transação.
            User saved = userRepository.saveAndFlush(user);
            return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            // Rede de segurança contra a race condition de dois registros concorrentes
            // com o mesmo email — o check existsByEmail acima não é atômico.
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Email inexistente, senha errada, conta locked ou desabilitada: todos resultam na mesma
        // InvalidCredentialsException, sem distinção, para não vazar qual condição falhou (nem no
        // código nem na resposta ao cliente).
        User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);

        if (user.isLocked() || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenPair(user);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        // Uma linha expirada não é limpa aqui — fora do escopo deste ciclo.
        RefreshToken refreshToken = findRefreshTokenOrThrow(
                rawRefreshToken, token -> token.getExpiresAt().isAfter(Instant.now()));

        // Deleção antes de emitir o novo par: consumo de uso único (Rotação — ADR-0008).
        refreshTokenRepository.delete(refreshToken);

        return issueTokenPair(refreshToken.getUser());
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        // Não confirma nem nega a existência do token para um usuário que não é o dono dele —
        // mesma exceção genérica de "não encontrado".
        RefreshToken refreshToken =
                findRefreshTokenOrThrow(rawRefreshToken, token -> token.getUser().getId().equals(userId));

        refreshTokenRepository.delete(refreshToken);
    }

    // Compartilhado por refresh/logout: token ausente, não encontrado, ou reprovado por
    // `constraint` (expiração em refresh, posse em logout) — mesmo 401 genérico em todos os
    // casos, sem distinguir a causa (spec do ciclo refresh/logout).
    private RefreshToken findRefreshTokenOrThrow(String rawRefreshToken, Predicate<RefreshToken> constraint) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return refreshTokenRepository
                .findByTokenHash(hashRefreshToken(rawRefreshToken))
                .filter(constraint)
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    // Compartilhado por login/refresh: emite o Access token (claims a partir das roles atuais do
    // User) e um novo Refresh token opaco, persistindo seu hash SHA-256 (ADR-0008).
    private LoginResponse issueTokenPair(User user) {
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        String accessToken = jwtService.generateAccessToken(user.getId(), roleNames);

        String rawRefreshToken = generateOpaqueRefreshToken();
        RefreshToken refreshToken =
                new RefreshToken(user, hashRefreshToken(rawRefreshToken), Instant.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
                accessToken, rawRefreshToken, "Bearer", jwtService.getAccessTokenTtl().toSeconds());
    }

    // Valor opaco de alta entropia (não JWT — ADR-0008); o texto plano só existe aqui e na
    // resposta ao cliente, nunca é persistido nem logado.
    private String generateOpaqueRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    // SHA-256 (não Argon2id): o refresh token já nasce de alta entropia, então o custo
    // memory-hard do Argon2id não agrega segurança aqui, só penaliza performance (ADR-0008).
    private String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawRefreshToken.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 é garantido por todo provider JCE padrão da JVM — nunca deveria ocorrer.
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
