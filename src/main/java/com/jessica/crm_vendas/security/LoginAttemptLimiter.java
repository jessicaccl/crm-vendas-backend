package com.jessica.crm_vendas.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jessica.crm_vendas.exception.TooManyLoginAttemptsException;

/**
 * Limita tentativas de login erradas para dificultar a adivinhação de senhas.
 *
 * Conta os erros por e-mail + IP (para que ninguém consiga bloquear a conta de outra pessoa,
 * como a demo pública, a partir de outro lugar) e por IP (para quem testa vários e-mails).
 * Ao passar do limite dentro da janela, aquele par fica bloqueado pelo mesmo período.
 * Os contadores ficam em memória: bastam para uma única instância da API e zeram ao reiniciar.
 */
@Component
public class LoginAttemptLimiter {

    /** Acima disso, entradas vencidas são descartadas para a memória não crescer sem limite. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxPerAccount;
    private final int maxPerIp;
    private final Duration window;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(
            Clock clock,
            @Value("${app.login.max-attempts:5}") int maxPerAccount,
            @Value("${app.login.max-attempts-per-ip:20}") int maxPerIp,
            @Value("${app.login.lock-duration:15m}") Duration window) {
        this.clock = clock;
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.window = window;
    }

    /** Lança {@link TooManyLoginAttemptsException} se este e-mail ou este IP estiver bloqueado. */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        long wait = Math.max(
                secondsLocked(accountKey(email, ip), now),
                secondsLocked(ipKey(ip), now));
        if (wait > 0) {
            throw new TooManyLoginAttemptsException(wait);
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.values().removeIf(entry -> entry.isExpired(now));
        }
        attempts.compute(accountKey(email, ip), (key, entry) -> increment(entry, now, maxPerAccount));
        attempts.compute(ipKey(ip), (key, entry) -> increment(entry, now, maxPerIp));
    }

    /** Login certo zera os erros daquele e-mail naquele IP (o contador do IP continua valendo). */
    public void recordSuccess(String email, String ip) {
        attempts.remove(accountKey(email, ip));
    }

    private long secondsLocked(String key, Instant now) {
        Attempts entry = attempts.get(key);
        if (entry == null || entry.lockedUntil() == null || !now.isBefore(entry.lockedUntil())) {
            return 0;
        }
        // Arredonda para cima: o cliente nunca é avisado para voltar antes do desbloqueio
        Duration left = Duration.between(now, entry.lockedUntil());
        return left.toSeconds() + (left.toNanosPart() > 0 ? 1 : 0);
    }

    private Attempts increment(Attempts entry, Instant now, int max) {
        Attempts current = entry == null || entry.isExpired(now) ? new Attempts(0, now.plus(window), null) : entry;
        int failures = current.failures() + 1;
        Instant lockedUntil = failures >= max ? now.plus(window) : current.lockedUntil();
        return new Attempts(failures, current.windowEnd(), lockedUntil);
    }

    private static String accountKey(String email, String ip) {
        return "account:" + email + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private record Attempts(int failures, Instant windowEnd, Instant lockedUntil) {
        boolean isExpired(Instant now) {
            Instant end = lockedUntil != null && lockedUntil.isAfter(windowEnd) ? lockedUntil : windowEnd;
            return !now.isBefore(end);
        }
    }
}
