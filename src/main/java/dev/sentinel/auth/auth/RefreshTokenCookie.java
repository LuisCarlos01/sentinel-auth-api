package dev.sentinel.auth.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;

/**
 * Cookie {@code httpOnly} do Refresh token, compartilhado entre {@code login} e {@code refresh}
 * (ADR-0009) — evita duplicar nome/atributos em cada endpoint.
 */
final class RefreshTokenCookie {

    static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";

    private RefreshTokenCookie() {}

    static ResponseCookie set(String rawRefreshToken, Duration maxAge) {
        return ResponseCookie.from(NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    // Max-Age=0 instrui o navegador a descartar o cookie imediatamente (usado em logout).
    static ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build();
    }

    // Ausente = null: quem chama decide o fallback (corpo do request), não esta classe.
    static String readFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
