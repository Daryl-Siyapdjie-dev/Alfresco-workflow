package com.kimia.bcrg_integration.pipeline;

/**
 * Journal des transmissions réussies, support de l'<strong>idempotence</strong> (§7) : évite de
 * renvoyer une feuille déjà transmise pour la même {@link IdempotencyKey}.
 * <p>L'implémentation par défaut est en mémoire ({@link InMemoryTransmissionLedger}) ; une
 * implémentation persistante (BD) pourra la remplacer sans changer le pipeline.
 */
public interface TransmissionLedger {

    boolean isAlreadySucceeded(IdempotencyKey key);

    void recordSuccess(IdempotencyKey key);
}
