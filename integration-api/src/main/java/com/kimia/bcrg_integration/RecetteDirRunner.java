package com.kimia.bcrg_integration;

import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationException;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationService;
import com.kimia.bcrg_integration.pipeline.DossierReport;
import com.kimia.bcrg_integration.pipeline.IntegrationResult;
import com.kimia.bcrg_integration.pipeline.TransmissionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Déclencheur de recette <strong>multi-fichiers</strong> (profil {@code recette}) : intègre
 * <em>tous</em> les classeurs {@code .xlsx} d'un dossier vers une même période, chacun via
 * {@link DossierIntegrationService} avec <strong>détection automatique</strong> du fichier
 * réglementaire (SITU/FINS/IGEC/PRUD/BALG/SIGIMF) d'après ses feuilles — donc envoi <strong>réel</strong>
 * à la BCRG, avec rejeu, idempotence et audit.
 *
 * <p>Complète {@link IntegrationSmokeRunner} (un seul fichier, code SIGIMF figé). Sert à l'aller-retour
 * de recette : remplir les templates, les envoyer à une période libre, puis les récupérer pour comparer.
 *
 * <p>Lancement type (une fin d'année couvre toutes les périodicités, donc aucune feuille sautée) :
 * <pre>
 * BCRG_USERNAME=… BCRG_PASSWORD=… mvn -o spring-boot:run -Dspring-boot.run.profiles=recette \
 *   -Dspring-boot.run.arguments="--bcrg.recette.dir=C:/chemin/remplis --bcrg.recette.year=2025 --bcrg.recette.month=12" \
 *   -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
 * </pre>
 * ({@code Windows-ROOT} est requis sur un poste dont l'antivirus intercepte le TLS — cf. outils/recette/README §1.)
 */
@Component
@Profile("recette")
public class RecetteDirRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecetteDirRunner.class);

    private final DossierIntegrationService dossiers;
    private final TransmissionFactory transmissions;
    private final org.springframework.context.ConfigurableApplicationContext context;

    @Value("${bcrg.recette.dir:}")
    private String dir;
    @Value("${bcrg.recette.year:2025}")
    private int year;
    @Value("${bcrg.recette.month:12}")
    private int month;

    public RecetteDirRunner(DossierIntegrationService dossiers, TransmissionFactory transmissions,
                            org.springframework.context.ConfigurableApplicationContext context) {
        this.dossiers = dossiers;
        this.transmissions = transmissions;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        if (dir == null || dir.isBlank()) {
            log.warn("Recette dossier : définir --bcrg.recette.dir=<dossier de classeurs remplis> pour lancer. Aucune action.");
            return;
        }
        File[] classeurs = new File(dir).listFiles((d, n) -> n.toLowerCase().endsWith(".xlsx"));
        if (classeurs == null || classeurs.length == 0) {
            log.warn("Recette dossier : aucun .xlsx dans {}", dir);
            return;
        }
        Arrays.sort(classeurs);

        TransmissionImf transmission = transmissions.forMonth(year, month, StatutEnum.CREATION);
        log.info("===== RECETTE — {} classeur(s), période {}/{} (dateArrete={}) =====",
                classeurs.length, year, month, transmission.getDateArrete());

        for (File f : classeurs) {
            log.info("--- {} ---", f.getName());
            try {
                DossierReport rapport = dossiers.run(f, DossierIntegrationService.CODE_FICHIER_AUTO,
                        transmission, List.of("TOUS"));
                for (IntegrationResult r : rapport.resultats()) {
                    log.info("  → {} : {} — {} items, HTTP {} — {}",
                            r.feuille(), r.outcome(), r.itemCount(), r.httpStatus(), r.detail());
                }
                log.info("  Bilan {} ({}) : {} feuille(s) — {}",
                        f.getName(), rapport.codeFichier(), rapport.total(), rapport.bilan());
            } catch (DossierIntegrationException | IllegalArgumentException e) {
                log.error("  {} : échec — {}", f.getName(), e.getMessage());
            }
        }
        log.info("===== RECETTE terminée =====");
        // Runner de lot : une fois l'envoi fait, on ne garde pas un serveur web en vie. Le code de
        // sortie 0 permet à l'appelant (spring-boot:run en tâche de fond) de rendre la main sans kill.
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> 0));
    }
}
