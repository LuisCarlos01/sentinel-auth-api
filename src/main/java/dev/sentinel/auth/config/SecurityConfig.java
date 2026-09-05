package dev.sentinel.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração mínima necessária para os endpoints de {@code /api/v1/auth} funcionarem sem
 * autenticação (ex.: {@code register}). Sem essa configuração, o auto-config default do Spring
 * Security exigiria HTTP Basic em toda a API. A cadeia completa de filtro JWT (login, refresh,
 * proteção de recursos autenticados) nasce em ciclos futuros da Phase 3.
 *
 * <p>CSRF desabilitado: a API é stateless via JWT, sem sessão nem cookie de sessão do servidor
 * (ver docs/architecture.md) — a proteção CSRF do Spring Security existe para autenticação
 * baseada em sessão/cookie, que este projeto não usa.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }
}
