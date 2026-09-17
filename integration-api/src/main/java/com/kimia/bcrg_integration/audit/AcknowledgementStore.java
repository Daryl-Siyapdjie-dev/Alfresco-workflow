package com.kimia.bcrg_integration.audit;

import java.util.List;
import java.util.Optional;

/**
 * Archive des accusés de réception (ticket 7.3) : « conservés et retrouvables ».
 * <p>Indexée par {@link AuditTarget} — un envoi ultérieur pour la même cible remplace l'accusé
 * précédent, qui n'a plus valeur de preuve.
 */
public interface AcknowledgementStore {

    void store(Acknowledgement accuse);

    /** L'accusé archivé pour cette cible, s'il en existe un. */
    Optional<Acknowledgement> find(AuditTarget cible);

    /** Tous les accusés archivés, du plus ancien au plus récent. */
    List<Acknowledgement> all();

    /** Archive inerte : pour les composants construits hors contexte Spring (tests, outils). */
    AcknowledgementStore NOOP = new AcknowledgementStore() {
        @Override public void store(Acknowledgement accuse) { /* rien à archiver */ }
        @Override public Optional<Acknowledgement> find(AuditTarget cible) { return Optional.empty(); }
        @Override public List<Acknowledgement> all() { return List.of(); }
    };
}
