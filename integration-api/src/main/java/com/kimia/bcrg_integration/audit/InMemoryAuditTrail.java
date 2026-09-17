package com.kimia.bcrg_integration.audit;


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Piste d'audit en mémoire, <strong>bornée</strong> : au-delà de {@code bcrg.audit.max-events}, les
 * événements les plus anciens sont évincés (un module qui tourne longtemps ne doit pas saturer la
 * mémoire). Thread-safe.
 * <p>Volatile par nature : à remplacer par une implémentation persistante pour une conservation
 * réglementaire au-delà du redémarrage.
 */
public class InMemoryAuditTrail implements AuditTrail {

    private final int maxEvents;
    private final Deque<AuditEvent> events = new ArrayDeque<>();

    public InMemoryAuditTrail(int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("bcrg.audit.max-events doit être >= 1, reçu : " + maxEvents);
        }
        this.maxEvents = maxEvents;
    }

    @Override
    public synchronized void append(AuditEvent evenement) {
        events.addLast(evenement);
        while (events.size() > maxEvents) {
            events.removeFirst();
        }
    }

    @Override
    public synchronized List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
