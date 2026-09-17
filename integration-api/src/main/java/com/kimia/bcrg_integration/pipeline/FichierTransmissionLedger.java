package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.persistance.JournalFichier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Journal des transmissions réussies, <strong>conservé sur disque</strong>.
 *
 * <p><strong>Le risque qu'il écarte.</strong> En mémoire, l'idempotence disparaît au redémarrage :
 * le module réenvoie alors une feuille déjà transmise, et SIRYF la refuse — un couple (feuille,
 * date d'arrêté) ne se transmet qu'une fois. Le refus n'est pas le pire : l'envoi consomme une
 * tentative, brouille la piste d'audit, et pour une feuille en {@code MODIFICATION} il pourrait
 * écraser une correction.
 *
 * <p>Une ligne par transmission, lisible telle quelle —
 * {@code SIGIMF|M.V.PFC|2026-03-31T00:00Z}. Le fichier est relu au démarrage : ce qui est parti
 * hier reste parti aujourd'hui.
 */
public class FichierTransmissionLedger implements TransmissionLedger {

    private static final Logger log = LoggerFactory.getLogger(FichierTransmissionLedger.class);

    private static final String SEPARATEUR = "|";

    private final JournalFichier journal;
    private final Set<IdempotencyKey> transmises = ConcurrentHashMap.newKeySet();

    public FichierTransmissionLedger(Path fichier) {
        this.journal = new JournalFichier(fichier);
        for (String ligne : journal.lignes()) {
            IdempotencyKey cle = relire(ligne);
            if (cle != null) {
                transmises.add(cle);
            }
        }
        log.info("Journal d'idempotence {} : {} transmission(s) déjà enregistrée(s)",
                journal.fichier(), transmises.size());
    }

    @Override
    public boolean isAlreadySucceeded(IdempotencyKey key) {
        return transmises.contains(key);
    }

    @Override
    public void recordSuccess(IdempotencyKey key) {
        if (transmises.add(key)) {
            journal.ajouter(ecrire(key));
        }
    }

    private static String ecrire(IdempotencyKey key) {
        return key.codeFichier() + SEPARATEUR + key.feuille() + SEPARATEUR + key.dateArrete();
    }

    /**
     * Relit une ligne du journal. Une ligne abîmée est <strong>ignorée</strong>, pas fatale : mieux
     * vaut perdre une entrée d'idempotence — le pire est alors un refus de SIRYF — que refuser de
     * démarrer parce qu'un fichier a été édité à la main.
     */
    private static IdempotencyKey relire(String ligne) {
        String[] parts = ligne.split("\\" + SEPARATEUR, 3);
        if (parts.length != 3) {
            log.warn("Journal d'idempotence : ligne ignorée (format inattendu) — {}", ligne);
            return null;
        }
        try {
            return new IdempotencyKey(parts[0], parts[1], OffsetDateTime.parse(parts[2]));
        } catch (RuntimeException dateIllisible) {
            log.warn("Journal d'idempotence : ligne ignorée (date illisible) — {}", ligne);
            return null;
        }
    }
}
