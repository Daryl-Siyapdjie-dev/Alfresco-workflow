package com.kimia.bcrg_integration.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide le backoff exponentiel plafonné du {@link RetryPolicy}. */
class RetryPolicyTest {

    @Test
    void backoff_exponentiel_plafonne() {
        RetryPolicy p = new RetryPolicy(4, 500L, 2.0, 8000L);
        assertEquals(500L, p.backoffMillis(1));
        assertEquals(1000L, p.backoffMillis(2));
        assertEquals(2000L, p.backoffMillis(3));
        assertEquals(4000L, p.backoffMillis(4));
        assertEquals(8000L, p.backoffMillis(5));   // 8000, au plafond
        assertEquals(8000L, p.backoffMillis(6));   // 16000 → plafonné à 8000
    }

    @Test
    void valeurs_par_defaut() {
        RetryPolicy d = RetryPolicy.defaults();
        assertEquals(4, d.maxAttempts());
        assertEquals(500L, d.initialBackoffMillis());
        assertEquals(8000L, d.maxBackoffMillis());
    }
}
