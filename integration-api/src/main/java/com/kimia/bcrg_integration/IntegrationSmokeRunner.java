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
import java.util.List;

/**
 * Déclencheur d'intégration de bout en bout (profil {@code smoketest}) : lit un vrai classeur SIGIMF
 * du disque et l'intègre via {@link DossierIntegrationService} — donc envoi <strong>réel</strong> à la
 * BCRG, avec rejeu, idempotence et audit. Ne s'exécute jamais au démarrage normal.
 *
 * <p>Lancement type (période ouverte 2026/août) :
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=smoketest \
 *   -Dspring-boot.run.arguments="--bcrg.smoketest.file=C:/chemin/SIGIMF.xlsx --bcrg.smoketest.sheet=M.III.HS_BILAN"
 * </pre>
 * ({@code BCRG_USERNAME}/{@code BCRG_PASSWORD} doivent être positionnés). {@code sheet=TOUS} traite
 * toutes les feuilles couvertes présentes dans le classeur.
 *
 * <p>Même traitement que l'endpoint interne {@code POST /api/internal/integration/sigimf} (Lot 8) :
 * les deux passent par le même service, seul le déclencheur diffère.
 */
@Component
@Profile("smoketest")
public class IntegrationSmokeRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSmokeRunner.class);

    private final DossierIntegrationService dossiers;
    private final TransmissionFactory transmissions;

    @Value("${bcrg.smoketest.file:}")
    private String filePath;
    @Value("${bcrg.smoketest.sheet:M.III.HS_BILAN}")
    private String sheet;
    @Value("${bcrg.smoketest.year:2026}")
    private int year;
    @Value("${bcrg.smoketest.month:8}")
    private int month;

    public IntegrationSmokeRunner(DossierIntegrationService dossiers, TransmissionFactory transmissions) {
        this.dossiers = dossiers;
        this.transmissions = transmissions;
    }

    @Override
    public void run(String... args) {
        if (filePath == null || filePath.isBlank()) {
            log.warn("Smoke test intégration : définir --bcrg.smoketest.file=<chemin .xlsx> pour lancer un envoi. "
                    + "Aucune action.");
            return;
        }

        TransmissionImf transmission = transmissions.forMonth(year, month, StatutEnum.CREATION);
        log.info("Smoke test intégration : fichier={}, feuille={}, période={}/{} (dateArrete={})",
                filePath, sheet, year, month, transmission.getDateArrete());

        try {
            DossierReport rapport = dossiers.run(new File(filePath),
                    DossierIntegrationService.CODE_FICHIER_SIGIMF, transmission, List.of(sheet));
            for (IntegrationResult r : rapport.resultats()) {
                log.info("  → {} : {} — {} items, HTTP {} — {}",
                        r.feuille(), r.outcome(), r.itemCount(), r.httpStatus(), r.detail());
            }
            log.info("Bilan dossier : {} feuille(s) — {}", rapport.total(), rapport.bilan());
        } catch (DossierIntegrationException e) {
            log.error("Smoke test intégration : échec d'ouverture/traitement du fichier {}", filePath, e);
        }
    }
}
