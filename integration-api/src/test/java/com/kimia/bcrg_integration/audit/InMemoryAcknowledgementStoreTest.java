package com.kimia.bcrg_integration.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 7.3 : les accusés sont « archivés et <strong>retrouvables</strong> ». La cible sert de clé,
 * donc un renvoi de la même feuille pour la même période remplace l'accusé précédent — c'est
 * volontaire : seule la dernière réponse de la BCRG fait foi.
 */
class InMemoryAcknowledgementStoreTest {

    private static final OffsetDateTime ARRETE =
            OffsetDateTime.of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final AuditTarget BILAN = new AuditTarget("SIGIMF", "M.I.BILAN", ARRETE);
    private static final AuditTarget HORS_BILAN = new AuditTarget("SIGIMF", "M.III.HS_BILAN", ARRETE);

    @Test
    void archive_et_retrouve_par_cible() {
        InMemoryAcknowledgementStore store = new InMemoryAcknowledgementStore();
        store.store(new Acknowledgement(BILAN, Instant.now(), 200, "accusé bilan"));
        store.store(new Acknowledgement(HORS_BILAN, Instant.now(), 200, "accusé hors bilan"));

        assertEquals(2, store.all().size());
        assertEquals("accusé bilan", store.find(BILAN).orElseThrow().corps());
        assertTrue(store.find(new AuditTarget("SIGIMF", "M.V.PFC", ARRETE)).isEmpty());
    }

    @Test
    void un_nouvel_accuse_remplace_le_precedent_pour_la_meme_cible() {
        InMemoryAcknowledgementStore store = new InMemoryAcknowledgementStore();
        store.store(new Acknowledgement(BILAN, Instant.now(), 200, "premier envoi"));
        store.store(new Acknowledgement(BILAN, Instant.now(), 200, "correction"));

        assertEquals(1, store.all().size());
        assertEquals("correction", store.find(BILAN).orElseThrow().corps());
    }
}
