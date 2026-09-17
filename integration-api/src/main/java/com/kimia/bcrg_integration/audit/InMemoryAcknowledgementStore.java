package com.kimia.bcrg_integration.audit;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Archive des accusés en mémoire (thread-safe), ordonnée par date d'arrivée.
 * <p>Comme {@code InMemoryTransmissionLedger}, elle ne survit pas au redémarrage : suffisante pour
 * le traitement d'un dossier et pour la relecture via l'API interne, à remplacer par une
 * persistance pour la conservation réglementaire.
 */
public class InMemoryAcknowledgementStore implements AcknowledgementStore {

    private final Map<AuditTarget, Acknowledgement> accuses = new LinkedHashMap<>();

    @Override
    public synchronized void store(Acknowledgement accuse) {
        accuses.remove(accuse.cible());      // réinsertion en fin : le plus récent reste le dernier
        accuses.put(accuse.cible(), accuse);
    }

    @Override
    public synchronized Optional<Acknowledgement> find(AuditTarget cible) {
        return Optional.ofNullable(accuses.get(cible));
    }

    @Override
    public synchronized List<Acknowledgement> all() {
        return List.copyOf(new ArrayList<>(accuses.values()));
    }
}
