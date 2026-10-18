package com.jessica.crm_vendas.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.jessica.crm_vendas.exception.TooManyLoginAttemptsException;

class LoginAttemptLimiterTest {

    private static final String IP = "200.1.2.3";

    /** Relógio que os testes podem adiantar. */
    private static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 20, Duration.ofMinutes(15));

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    @Test
    void allowsUpToFourWrongAttempts() {
        fail("ana@x.com", IP, 4);

        assertThatCode(() -> limiter.checkAllowed("ana@x.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void blocksAfterFiveWrongAttemptsAndTellsHowLongToWait() {
        fail("ana@x.com", IP, 5);

        assertThatThrownBy(() -> limiter.checkAllowed("ana@x.com", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessage("Muitas tentativas de login. Tente novamente em 15 minutos.")
                .extracting(e -> ((TooManyLoginAttemptsException) e).getRetryAfterSeconds())
                .isEqualTo(900L);
    }

    @Test
    void unblocksWhenTheLockExpires() {
        fail("ana@x.com", IP, 5);

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkAllowed("ana@x.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void errorsOutsideTheWindowDoNotAccumulate() {
        fail("ana@x.com", IP, 4);
        clock.advance(Duration.ofMinutes(16));
        fail("ana@x.com", IP, 4);

        assertThatCode(() -> limiter.checkAllowed("ana@x.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void correctLoginResetsTheAccountCounter() {
        fail("ana@x.com", IP, 4);
        limiter.recordSuccess("ana@x.com", IP);
        fail("ana@x.com", IP, 4);

        assertThatCode(() -> limiter.checkAllowed("ana@x.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void someoneElseCannotLockTheAccountFromAnotherIp() {
        // Importante para a demo pública: errar a senha dela de outro lugar não bloqueia quem está usando
        fail("demo@crmpro.dev", "9.9.9.9", 5);

        assertThatCode(() -> limiter.checkAllowed("demo@crmpro.dev", IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("demo@crmpro.dev", "9.9.9.9"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void blocksAnIpThatTriesManyDifferentEmails() {
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("pessoa" + i + "@x.com", IP);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("nova@x.com", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("nova@x.com", "1.1.1.1")).doesNotThrowAnyException();
    }
}
