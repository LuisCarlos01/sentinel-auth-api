package dev.sentinel.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o suporte a {@code @Scheduled} do Spring — exigido por
 * {@code RefreshTokenCleanupJob}; sem isso, seu método agendado nunca dispara.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
