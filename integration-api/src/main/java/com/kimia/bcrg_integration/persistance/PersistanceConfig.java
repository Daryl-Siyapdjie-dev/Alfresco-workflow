package com.kimia.bcrg_integration.persistance;

import com.kimia.bcrg_integration.audit.AcknowledgementStore;
import com.kimia.bcrg_integration.audit.AuditTrail;
import com.kimia.bcrg_integration.audit.FichierAcknowledgementStore;
import com.kimia.bcrg_integration.audit.FichierAuditTrail;
import com.kimia.bcrg_integration.audit.InMemoryAcknowledgementStore;
import com.kimia.bcrg_integration.audit.InMemoryAuditTrail;
import com.kimia.bcrg_integration.pipeline.FichierTransmissionLedger;
import com.kimia.bcrg_integration.pipeline.InMemoryTransmissionLedger;
import com.kimia.bcrg_integration.pipeline.TransmissionLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Choix de la conservation des trois journaux du module : idempotence, piste d'audit, accusés.
 *
 * <p>Un seul réglage décide, {@code bcrg.persistance.repertoire} :
 * <ul>
 *   <li><strong>renseigné</strong> → journaux sur disque, qui <strong>survivent au redémarrage</strong>.
 *       C'est le mode attendu en production : sans lui, le module réenvoie une feuille déjà transmise
 *       après un simple redéploiement, et la piste d'audit réglementaire (§9) repart à zéro ;</li>
 *   <li><strong>vide</strong> → journaux en mémoire. Le mode des tests et des essais : rien à nettoyer,
 *       rien à isoler entre deux exécutions.</li>
 * </ul>
 *
 * <p>Le choix est fait par un {@code if} explicite plutôt que par des conditions Spring : à la
 * lecture, on voit d'un coup d'œil ce qui tourne et pourquoi — et un démarrage journalise le mode
 * retenu, pour qu'une mise en production oubliée se remarque dans les premières lignes de log.
 */
@Configuration
public class PersistanceConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistanceConfig.class);

    private final Path repertoire;

    public PersistanceConfig(@Value("${bcrg.persistance.repertoire:}") String repertoire) {
        this.repertoire = repertoire.isBlank() ? null : Path.of(repertoire.trim());
        if (this.repertoire == null) {
            log.warn("Persistance désactivée (bcrg.persistance.repertoire non renseigné) : "
                    + "idempotence et piste d'audit ne survivront pas au redémarrage");
        } else {
            log.info("Persistance des journaux dans {}", this.repertoire.toAbsolutePath());
        }
    }

    @Bean
    public TransmissionLedger transmissionLedger() {
        return repertoire == null
                ? new InMemoryTransmissionLedger()
                : new FichierTransmissionLedger(repertoire.resolve("transmissions.journal"));
    }

    @Bean
    public AuditTrail auditTrail(@Value("${bcrg.audit.max-events:5000}") int maxEvents) {
        return repertoire == null
                ? new InMemoryAuditTrail(maxEvents)
                : new FichierAuditTrail(repertoire.resolve("audit.journal"), maxEvents);
    }

    @Bean
    public AcknowledgementStore acknowledgementStore() {
        return repertoire == null
                ? new InMemoryAcknowledgementStore()
                : new FichierAcknowledgementStore(repertoire.resolve("accuses.journal"));
    }
}
