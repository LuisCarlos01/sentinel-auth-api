package dev.sentinel.auth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return withRefreshTokenCookie(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
        String bodyToken = request != null ? request.refreshToken() : null;
        LoginResponse response = authService.refresh(resolveRefreshToken(httpRequest, bodyToken));
        return withRefreshTokenCookie(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        String bodyToken = request != null ? request.refreshToken() : null;
        UUID userId = UUID.fromString(authentication.getName());
        authService.logout(userId, resolveRefreshToken(httpRequest, bodyToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString())
                .build();
    }

    // Cookie prevalece sobre o corpo quando os dois vêm preenchidos (ADR-0009).
    private String resolveRefreshToken(HttpServletRequest httpRequest, String bodyToken) {
        String cookieToken = RefreshTokenCookie.readFrom(httpRequest);
        return cookieToken != null ? cookieToken : bodyToken;
    }

    private ResponseEntity<LoginResponse> withRefreshTokenCookie(LoginResponse response) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshTokenCookie.set(response.refreshToken(), authService.getRefreshTokenTtl())
                                .toString())
                .body(response);
    }
}
