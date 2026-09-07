package dev.sentinel.auth.config;

import dev.sentinel.auth.auth.JwtAuthenticationFilter;
import dev.sentinel.auth.auth.JwtService;
import dev.sentinel.auth.auth.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /api/v1/auth/register}, {@code /login} e {@code /refresh} continuam públicos;
 * {@code /logout} (e qualquer rota futura) exige um Access token válido, verificado pelo
 * {@link JwtAuthenticationFilter}. Falhas de autenticação são traduzidas para RFC 9457 pelo
 * {@link RestAuthenticationEntryPoint}.
 *
 * <p>CSRF desabilitado: a API é stateless via JWT, sem sessão nem cookie de sessão do servidor
 * (ver docs/architecture.md) — a proteção CSRF do Spring Security existe para autenticação
 * baseada em sessão/cookie, que este projeto não usa.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, JsonMapper jsonMapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(new RestAuthenticationEntryPoint(jsonMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
