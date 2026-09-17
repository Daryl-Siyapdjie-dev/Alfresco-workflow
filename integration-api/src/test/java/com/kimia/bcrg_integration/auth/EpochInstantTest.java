package com.kimia.bcrg_integration.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vérifie l'heuristique de conversion epoch → Instant de {@link AuthClient#toInstant(long)}
 * (détection seconde vs milliseconde).
 */
class EpochInstantTest {

    @Test
    void interprete_une_valeur_a_10_chiffres_comme_des_secondes() {
        long epochSeconds = 1_754_000_000L; // ~ 2025, 10 chiffres
        assertEquals(Instant.ofEpochSecond(epochSeconds), AuthClient.toInstant(epochSeconds));
    }

    @Test
    void interprete_une_valeur_a_13_chiffres_comme_des_millisecondes() {
        long epochMillis = 1_754_000_000_000L; // ~ 2025, 13 chiffres
        assertEquals(Instant.ofEpochMilli(epochMillis), AuthClient.toInstant(epochMillis));
    }

    @Test
    void le_seuil_1e12_bascule_de_secondes_a_millisecondes() {
        assertEquals(Instant.ofEpochSecond(999_999_999_999L), AuthClient.toInstant(999_999_999_999L));
        assertEquals(Instant.ofEpochMilli(1_000_000_000_000L), AuthClient.toInstant(1_000_000_000_000L));
    }
}
