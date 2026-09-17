package com.kimia.bcrg_integration.pipeline;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Journal d'idempotence en mémoire (thread-safe). Suffisant pour l'exécution d'un dossier ; à
 * remplacer par une implémentation persistante pour survivre aux redémarrages (Lot ultérieur).
 */
public class InMemoryTransmissionLedger implements TransmissionLedger {

    private final Set<IdempotencyKey> succeeded = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isAlreadySucceeded(IdempotencyKey key) {
        return succeeded.contains(key);
    }

    @Override
    public void recordSuccess(IdempotencyKey key) {
        succeeded.add(key);
    }
}
