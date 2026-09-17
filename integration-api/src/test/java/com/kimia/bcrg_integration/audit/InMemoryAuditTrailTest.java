package com.kimia.bcrg_integration.audit;

import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La piste est <strong>bornée</strong> : un module qui tourne des semaines ne doit pas accumuler
 * indéfiniment. On vérifie que l'éviction porte bien sur les événements les plus anciens (l'ordre
 * chronologique est ce qui fait la valeur d'une piste d'audit).
 */
class InMemoryAuditTrailTest {

    private static final OffsetDateTime ARRETE =
            OffsetDateTime.of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    private AuditEvent evenement(String feuille) {
        return new AuditEvent(Instant.now(), "test", AuditAction.IMPORT,
                new AuditTarget("SIGIMF", feuille, ARRETE), Resultat.SUCCES, 200, "ok", 1);
    }

    @Test
    void conserve_les_plus_recents_et_evince_les_plus_anciens() {
        InMemoryAuditTrail trail = new InMemoryAuditTrail(3);
        for (String feuille : List.of("A", "B", "C", "D", "E")) {
            trail.append(evenement(feuille));
        }

        List<AuditEvent> events = trail.events();
        assertEquals(3, events.size());
        assertEquals("C", events.get(0).cible().feuille());
        assertEquals("E", events.get(2).cible().feuille());
    }

    @Test
    void recent_rend_les_n_derniers() {
        InMemoryAuditTrail trail = new InMemoryAuditTrail(10);
        for (String feuille : List.of("A", "B", "C")) {
            trail.append(evenement(feuille));
        }

        assertEquals(List.of("B", "C"), trail.recent(2).stream().map(e -> e.cible().feuille()).toList());
        assertEquals(3, trail.recent(99).size());
        assertEquals(3, trail.recent(0).size());
    }

    @Test
    void refuse_une_taille_absurde() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryAuditTrail(0));
    }
}
