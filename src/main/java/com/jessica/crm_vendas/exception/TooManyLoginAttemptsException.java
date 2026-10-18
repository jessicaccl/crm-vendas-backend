package com.jessica.crm_vendas.exception;

/** Login bloqueado temporariamente por excesso de tentativas erradas (responde 429). */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Muitas tentativas de login. Tente novamente em " + minutes(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String minutes(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
