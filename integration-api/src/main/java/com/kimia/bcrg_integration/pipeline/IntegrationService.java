package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.audit.AuditAction;
import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.Auditor;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.client.ReponseSiryf;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetTable;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import com.kimia.bcrg_integration.mapping.MappingException;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chef d'orchestre de l'intégration d'<strong>une feuille</strong> : enchaîne les quatre maillons
 * décrits par un {@link SheetJob} — lecture Excel → mapping → assemblage du payload → envoi — et
 * remonte un {@link IntegrationResult} classé (SUCCES / ERREUR_DONNEES / ERREUR_API / ERREUR_TECHNIQUE).
 * <p>Générique : ne connaît ni le type d'items ni le {@code DataModel} concret. L'envoi est fourni par
 * le {@code sender} du job, ce qui rend l'orchestration testable hors-ligne (sans réseau ni identifiants).
 * <p>Chaque exécution émet deux événements d'audit (Lot 7, ticket 7.2) — {@code TRANSFORMATION} puis
 * {@code IMPORT} — et archive l'accusé de réception des envois acceptés.
 */
@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final ExcelReader reader;
    private final Auditor auditor;

    @Autowired
    public IntegrationService(ExcelReader reader, Auditor auditor) {
        this.reader = reader;
        this.auditor = auditor;
    }

    /** Variante sans audit : pour un usage hors contexte Spring (tests, outils en ligne de commande). */
    public IntegrationService(ExcelReader reader) {
        this(reader, Auditor.noop());
    }

    /** Intègre une feuille sans connaître le fichier réglementaire d'origine (audit partiel). */
    public <I, D> IntegrationResult run(Workbook workbook, SheetJob<I, D> job, TransmissionImf transmission) {
        return run(workbook, job, transmission, null);
    }

    public <I, D> IntegrationResult run(Workbook workbook, SheetJob<I, D> job,
                                        TransmissionImf transmission, String codeFichier) {
        String feuille = job.sheetName();
        AuditTarget cible = new AuditTarget(codeFichier, feuille, transmission.getDateArrete());
        long debut = System.nanoTime();
        int itemCount = 0;
        try {
            // 1+2) lecture puis mapping, bloc par bloc — une feuille peut juxtaposer plusieurs
            //      tableaux de colonnes différentes qui alimentent le même envoi (ex. M.XV.RECAP)
            List<I> items = new ArrayList<>();
            for (SheetBlock<I> block : job.blocks()) {
                SheetTable table = reader.readSheet(workbook, feuille, block.layout());
                items.addAll(block.mapper().map(table));
            }
            itemCount = items.size();

            // 3) assemblage du payload (items + en-tête de transmission)
            D payload = job.assembler().apply(items, transmission);

            // 3 bis) second tableau, s'il y en a un : une autre nature de données, dans sa propre
            //        liste « items2 » — les charges ne partent pas parmi les produits (FINS_09…)
            if (job.secondTable() != null) {
                SheetTable table2 = reader.readSheet(workbook, feuille, job.secondTable().layout());
                itemCount += job.secondTable().mapInto(table2, payload);
            }
            auditor.succes(AuditAction.TRANSFORMATION, cible, itemCount + " items mappés", ecoule(debut));

            // 4) envoi
            long avantEnvoi = System.nanoTime();
            ResponseEntity<String> response = job.sender().apply(payload);

            int http = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                auditor.accuse(cible, http, response.getBody());   // ticket 7.3

                // Un 2xx ne suffit pas : SIRYF accepte la transmission tout en refusant des lignes,
                // et le dit dans son corps de réponse. Le lire est le seul moyen de ne pas annoncer
                // un succès complet là où la donnée est partie amputée.
                ReponseSiryf controles = ReponseSiryf.lire(response.getBody());

                // Un refus de la remise entière peut arriver AVEC un 200 : SIRYF le dit alors dans
                // son « statutCode », pas dans son code HTTP. Le prendre pour un succès marquerait
                // la feuille comme transmise et la dispenserait de tout rejeu ultérieur.
                if (controles.refusee()) {
                    log.warn("Feuille {} : HTTP {} mais remise refusée — {}", feuille, http,
                            controles.refus());
                    auditor.echec(AuditAction.IMPORT, cible, http, controles.refus(), ecoule(avantEnvoi));
                    return IntegrationResult.erreurApi(feuille, itemCount, http, controles.refus());
                }
                if (!controles.sansErreur()) {
                    log.warn("Feuille {} : transmission acceptée (HTTP {}) mais {}",
                            feuille, http, controles.resume());
                    auditor.etape(AuditAction.IMPORT, cible, Resultat.SUCCES, http,
                            itemCount + " items transmis — " + controles.resume(), ecoule(avantEnvoi));
                    return IntegrationResult.accepteAvecErreurs(feuille, itemCount, http,
                            controles.resume());
                }
                log.info("Feuille {} : {} items envoyés, HTTP {}", feuille, itemCount, http);
                auditor.etape(AuditAction.IMPORT, cible, Resultat.SUCCES,
                        http, itemCount + " items acceptés", ecoule(avantEnvoi));
                return IntegrationResult.succes(feuille, itemCount, http, response.getBody());
            }
            log.warn("Feuille {} : réponse non 2xx (HTTP {})", feuille, http);
            auditor.echec(AuditAction.IMPORT, cible, http, response.getBody(), ecoule(avantEnvoi));
            return IntegrationResult.erreurApi(feuille, itemCount, http, response.getBody());

        } catch (MappingException | IllegalArgumentException | IllegalStateException e) {
            // erreurs d'entrée (feuille/colonne manquante, donnée invalide) → correction métier, pas de rejeu (§7)
            log.warn("Feuille {} : erreur de lecture/données — {}", feuille, e.getMessage());
            auditor.echec(AuditAction.TRANSFORMATION, cible, null, e.getMessage(), ecoule(debut));
            return IntegrationResult.erreurDonnees(feuille, itemCount, e.getMessage());

        } catch (BcrgApiException e) {
            // 4xx = fonctionnel ; 5xx = technique/rejouable (classification portée par l'exception)
            log.warn("Feuille {} : erreur API {} — {}", feuille, e.getHttpStatus(), e.getMessage());
            auditor.echec(AuditAction.IMPORT, cible, e.getHttpStatus(), e.getMessage(), ecoule(debut));
            return IntegrationResult.erreurApi(feuille, itemCount, e.getHttpStatus(), e.getMessage());

        } catch (Exception e) {
            log.error("Feuille {} : erreur technique inattendue", feuille, e);
            auditor.echec(AuditAction.IMPORT, cible, null, e.getMessage(), ecoule(debut));
            return IntegrationResult.erreurTechnique(feuille, itemCount, e.getMessage());
        }
    }

    private static long ecoule(long departNanos) {
        return (System.nanoTime() - departNanos) / 1_000_000;
    }
}
