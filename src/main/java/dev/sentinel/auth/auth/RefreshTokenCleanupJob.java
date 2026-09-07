package dev.sentinel.auth.auth;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Remove periodicamente Refresh tokens expirados de {@code refresh_tokens} — sem isso, uma
 * sessão abandonada (ninguém nunca tenta {@code refresh} de novo) deixa a linha morta no banco
 * indefinidamente, já que {@code refresh}/{@code logout} só tratam expiração como inválida, sem
 * limpar a linha (ver {@code AuthService}).
 *
 * <p>Componente próprio, separado de {@link AuthService}: não mistura orquestração do fluxo de
 * autenticação com rotina de manutenção em background.
 */
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Método público testável diretamente, sem depender do agendador do Spring disparar. */
    @Scheduled(fixedRateString = "${sentinel.jwt.refresh-token-cleanup-interval-ms}")
    public void cleanUpExpiredTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(Instant.now());
    }
}
