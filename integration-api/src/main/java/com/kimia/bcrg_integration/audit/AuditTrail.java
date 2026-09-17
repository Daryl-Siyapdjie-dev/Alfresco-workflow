package com.kimia.bcrg_integration.audit;

import java.util.List;

/**
 * Piste d'audit : journal ordonné des {@link AuditEvent} (ticket 7.1).
 * <p>L'implémentation par défaut est en mémoire ({@link InMemoryAuditTrail}) ; une implémentation
 * persistante (BD, fichier) pourra la remplacer sans toucher au pipeline — même contrat que le
 * {@code TransmissionLedger} de l'idempotence.
 */
public interface AuditTrail {

    /** Ajoute un événement à la piste (le plus récent en dernier). */
    void append(AuditEvent evenement);

    /** Tous les événements conservés, du plus ancien au plus récent. */
    List<AuditEvent> events();

    /** Les {@code limit} événements les plus récents ({@code limit <= 0} = tous). */
    default List<AuditEvent> recent(int limit) {
        List<AuditEvent> tous = events();
        if (limit <= 0 || limit >= tous.size()) {
            return tous;
        }
        return List.copyOf(tous.subList(tous.size() - limit, tous.size()));
    }

    /** Piste inerte : utilisée par les composants construits hors contexte Spring (tests, outils). */
    AuditTrail NOOP = new AuditTrail() {
        @Override public void append(AuditEvent evenement) { /* rien à journaliser */ }
        @Override public List<AuditEvent> events() { return List.of(); }
    };
}
