package com.kimia.bcrg_integration.persistance;

import com.kimia.bcrg_integration.audit.Acknowledgement;
import com.kimia.bcrg_integration.audit.AuditAction;
import com.kimia.bcrg_integration.audit.AuditEvent;
import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.FichierAcknowledgementStore;
import com.kimia.bcrg_integration.audit.FichierAuditTrail;
import com.kimia.bcrg_integration.pipeline.FichierTransmissionLedger;
import com.kimia.bcrg_integration.pipeline.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les trois journaux du module — idempotence, piste d'audit, accusés — doivent
 * <strong>survivre au redémarrage</strong>. C'est la seule propriété qui compte ici, et chaque
 * épreuve la vérifie de la même façon : on écrit avec une instance, puis on en crée une
 * <em>nouvelle</em> sur le même fichier. Créer une seconde instance, c'est exactement ce que fait un
 * redémarrage.
 *
 * <p>Sans cela, le module réenvoie après un redéploiement une feuille déjà transmise — constaté en
 * recette le 27/08/2026, SIRYF répondant « cette date d'arrêté est déjà utilisée » — et la
 * traçabilité réglementaire (§9) repart de zéro.
 */
class PersistanceTest {

    private static final OffsetDateTime ARRETE =
            OffsetDateTime.of(2026, 3, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    // ------------------------------------------------------------------ idempotence

    @Test
    void une_transmission_enregistree_reste_connue_apres_redemarrage(@TempDir Path dossier) {
        Path journal = dossier.resolve("transmissions.journal");
        IdempotencyKey cle = new IdempotencyKey("SIGIMF", "M.V.PFC", ARRETE);

        FichierTransmissionLedger avant = new FichierTransmissionLedger(journal);
        assertFalse(avant.isAlreadySucceeded(cle));
        avant.recordSuccess(cle);
        assertTrue(avant.isAlreadySucceeded(cle));

        // redémarrage : une nouvelle instance, le même fichier
        FichierTransmissionLedger apres = new FichierTransmissionLedger(journal);
        assertTrue(apres.isAlreadySucceeded(cle),
                "sans cela, la feuille repartirait et SIRYF refuserait la période");
        assertFalse(apres.isAlreadySucceeded(new IdempotencyKey("SIGIMF", "M.I.BILAN", ARRETE)),
                "une autre feuille reste à transmettre");
    }

    @Test
    void un_journal_d_idempotence_abime_n_empeche_pas_de_demarrer(@TempDir Path dossier)
            throws Exception {
        Path journal = dossier.resolve("transmissions.journal");
        new FichierTransmissionLedger(journal).recordSuccess(
                new IdempotencyKey("SITU", "SITU_01", ARRETE));
        // coupure d'alimentation en pleine écriture, ou édition à la main
        Files.writeString(journal, "ligne|abimee" + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        FichierTransmissionLedger apres = new FichierTransmissionLedger(journal);

        assertTrue(apres.isAlreadySucceeded(new IdempotencyKey("SITU", "SITU_01", ARRETE)),
                "la ligne saine qui précède ne doit pas être perdue avec la ligne abîmée");
    }

    // ------------------------------------------------------------------ piste d'audit

    @Test
    void la_piste_d_audit_traverse_le_redemarrage(@TempDir Path dossier) {
        Path journal = dossier.resolve("audit.journal");
        AuditTarget cible = new AuditTarget("SIGIMF", "M.III.HS_BILAN", ARRETE);

        new FichierAuditTrail(journal, 100).append(new AuditEvent(
                Instant.parse("2026-03-31T10:15:30Z"), "validateur.diallo", AuditAction.IMPORT,
                cible, Resultat.SUCCES, 200, "12 items acceptés", 842));

        List<AuditEvent> apres = new FichierAuditTrail(journal, 100).events();

        assertEquals(1, apres.size());
        AuditEvent e = apres.get(0);
        assertEquals("validateur.diallo", e.acteur(), "qui a déclenché l'envoi");
        assertEquals(AuditAction.IMPORT, e.action());
        assertEquals(Resultat.SUCCES, e.resultat());
        assertEquals(200, e.httpStatus());
        assertEquals(842, e.dureeMillis());
        assertEquals("12 items acceptés", e.detail());
        assertEquals(cible, e.cible(), "fichier, feuille et période sont restitués");
    }

    /** Un détail multiligne ne doit pas casser le format : une ligne du fichier = un événement. */
    @Test
    void un_detail_multiligne_reste_sur_une_seule_ligne(@TempDir Path dossier) {
        Path journal = dossier.resolve("audit.journal");
        new FichierAuditTrail(journal, 100).append(new AuditEvent(
                Instant.now(), "bcrg-integration", AuditAction.IMPORT, null, Resultat.ECHEC, 409,
                "refus :\nligne 4 : Code X est introuvable\nligne 9 : écart", 10));

        List<AuditEvent> apres = new FichierAuditTrail(journal, 100).events();

        assertEquals(1, apres.size(), "un événement, pas trois");
        assertTrue(apres.get(0).detail().contains("Code X est introuvable"));
    }

    @Test
    void la_fenetre_rechargee_est_bornee(@TempDir Path dossier) {
        Path journal = dossier.resolve("audit.journal");
        FichierAuditTrail avant = new FichierAuditTrail(journal, 100);
        for (int i = 0; i < 20; i++) {
            avant.append(new AuditEvent(Instant.now(), "acteur", AuditAction.DEPOT, null,
                    Resultat.SUCCES, null, "événement " + i, 0));
        }

        // le fichier garde tout ; la fenêtre en mémoire, elle, reste bornée
        FichierAuditTrail apres = new FichierAuditTrail(journal, 5);

        assertEquals(5, apres.events().size());
        assertEquals("événement 19", apres.events().get(4).detail(), "les plus récents");
    }

    // ------------------------------------------------------------------ accusés

    @Test
    void un_accuse_reste_retrouvable_apres_redemarrage(@TempDir Path dossier) {
        Path journal = dossier.resolve("accuses.journal");
        AuditTarget cible = new AuditTarget("PRUD", "ECNP", ARRETE);
        String corps = "{\"erreurs\":[],\"description\":\"Traitement effectué avec succès\"}";

        new FichierAcknowledgementStore(journal).store(
                new Acknowledgement(cible, Instant.parse("2026-03-31T10:16:00Z"), 200, corps));

        FichierAcknowledgementStore apres = new FichierAcknowledgementStore(journal);

        assertTrue(apres.find(cible).isPresent(), "la pièce justificative de l'envoi");
        assertEquals(200, apres.find(cible).orElseThrow().httpStatus());
        assertEquals(corps, apres.find(cible).orElseThrow().corps());
        assertEquals(1, apres.all().size());
    }

    /** Un envoi rejoué remplace l'accusé précédent dans la vue, sans effacer l'historique du fichier. */
    @Test
    void le_dernier_accuse_l_emporte_dans_la_vue(@TempDir Path dossier) {
        Path journal = dossier.resolve("accuses.journal");
        AuditTarget cible = new AuditTarget("PRUD", "FPN", ARRETE);
        FichierAcknowledgementStore store = new FichierAcknowledgementStore(journal);
        store.store(new Acknowledgement(cible, Instant.parse("2026-03-31T10:00:00Z"), 200, "premier"));
        store.store(new Acknowledgement(cible, Instant.parse("2026-03-31T11:00:00Z"), 200, "second"));

        FichierAcknowledgementStore apres = new FichierAcknowledgementStore(journal);

        assertEquals(1, apres.all().size(), "une entrée par cible");
        assertEquals("second", apres.find(cible).orElseThrow().corps());
    }
}
