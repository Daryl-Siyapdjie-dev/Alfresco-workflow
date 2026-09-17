package com.kimia.bcrg_integration.recette;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.pipeline.*;
import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outil de recette — <strong>passage à blanc</strong> des classeurs remplis (README §6).
 * <p>Fait traverser chaque classeur du dossier {@code -Drecette.remplis=<dir>} par le vrai pipeline
 * (lecture POI avec recalcul des formules + tous les mappers), avec un {@link BcrgSender} factice :
 * aucun appel réseau, aucune période consommée. Vérifie que toutes les feuilles mappent et journalise
 * les endpoints qui seraient appelés. C'est le préalable obligatoire à un envoi réel.
 * <p>Ne s'exécute que si {@code recette.remplis} est fourni — invisible en build normal.
 */
@EnabledIfSystemProperty(named = "recette.remplis", matches = ".+")
class DryRunRemplisTest {

    /** dateArrete de fin d'année : accepte toutes les périodicités (mensuel→annuel), donc aucune feuille sautée. */
    private TransmissionImf finAnnee() {
        return new TransmissionFactory().creationForMonth(2026, 12);
    }

    private DossierIntegrationService service(List<String> endpoints) {
        AtomicInteger n = new AtomicInteger();
        BcrgSender factice = (uri, body) -> {
            endpoints.add(uri);
            n.incrementAndGet();
            return ResponseEntity.ok("{\"typeMessageRetour\":\"SUCCES\"}");
        };
        RetryingIntegrationService resilient = new RetryingIntegrationService(
                new IntegrationService(new ExcelReader()),
                new RetryPolicy(1, 1L, 2.0, 2L),
                new InMemoryTransmissionLedger(),
                millis -> { });
        JobRegistries registres = new JobRegistries(
                new SigimfJobRegistry(factice), new SituJobRegistry(factice),
                new FinsJobRegistry(factice), new IgecJobRegistry(factice),
                new PrudJobRegistry(factice), new BalgJobRegistry(factice));
        return new DossierIntegrationService(registres, resilient);
    }

    @Test
    void passage_a_blanc_de_tous_les_classeurs_remplis() {
        File dir = new File(System.getProperty("recette.remplis"));
        File[] classeurs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xlsx"));
        assert classeurs != null && classeurs.length > 0 : "aucun .xlsx dans " + dir;

        int totalErreurs = 0;
        System.out.println("\n===== PASSAGE A BLANC (dry-run) — " + dir + " =====");
        for (File f : classeurs) {
            List<String> endpoints = new ArrayList<>();
            DossierReport rapport;
            try {
                rapport = service(endpoints).run(f, DossierIntegrationService.CODE_FICHIER_AUTO,
                        finAnnee(), List.of("TOUS"));
            } catch (RuntimeException e) {
                System.out.println("\n## " + f.getName() + " : ECHEC OUVERTURE/DETECTION — " + e.getMessage());
                totalErreurs++;
                continue;
            }
            System.out.println("\n## " + f.getName() + "  [" + rapport.codeFichier() + "]  "
                    + rapport.bilan());
            for (IntegrationResult r : rapport.resultats()) {
                if (r.outcome() == Outcome.ERREUR_DONNEES || r.outcome() == Outcome.ERREUR_TECHNIQUE) {
                    System.out.println("   ✗ " + r.feuille() + " : " + r.outcome() + " — " + r.detail());
                    totalErreurs++;
                }
            }
            long succes = rapport.resultats().stream().filter(r -> r.outcome() == Outcome.SUCCES).count();
            long horsP = rapport.resultats().stream().filter(r -> r.outcome() == Outcome.HORS_PERIODE).count();
            System.out.println("   → " + succes + " feuille(s) mappée(s)+envoyée(s) (factice), "
                    + horsP + " hors période, " + endpoints.size() + " endpoint(s) appelé(s)");
        }
        System.out.println("\n===== BILAN DRY-RUN : " + totalErreurs + " erreur(s) de mapping =====\n");
    }
}
