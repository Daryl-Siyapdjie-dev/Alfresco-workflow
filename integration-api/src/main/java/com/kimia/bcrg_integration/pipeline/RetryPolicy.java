package com.kimia.bcrg_integration.pipeline;

/**
 * Politique de rejeu à backoff exponentiel (cf. §7 du CDC).
 *
 * @param maxAttempts         nombre total de tentatives, y compris la première (ex. 4 = 1 essai + 3 rejeux)
 * @param initialBackoffMillis délai avant le 1er rejeu
 * @param multiplier          facteur multiplicatif entre deux rejeux
 * @param maxBackoffMillis    plafond du délai
 */
public record RetryPolicy(int maxAttempts, long initialBackoffMillis, double multiplier, long maxBackoffMillis) {

    /** Valeurs par défaut raisonnables : 4 tentatives, 500 ms → 1 s → 2 s (plafond 8 s). */
    public static RetryPolicy defaults() {
        return new RetryPolicy(4, 500L, 2.0, 8000L);
    }

    /**
     * Délai (ms) avant le rejeu numéro {@code attempt} (1 = premier rejeu). Croît géométriquement,
     * plafonné à {@link #maxBackoffMillis}.
     */
    public long backoffMillis(int attempt) {
        double delay = initialBackoffMillis * Math.pow(multiplier, Math.max(0, attempt - 1));
        return (long) Math.min(delay, maxBackoffMillis);
    }
}
