package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traitement d'un dossier complet (Lot 8) : c'est la boucle que partagent le runner de smoke test et
 * l'endpoint interne. On vérifie surtout ses règles de tolérance — « TOUS » traite ce qui est
 * présent sans exiger l'exhaustivité, une feuille demandée mais absente est une erreur imputée à
 * l'appelant, et une feuille en échec n'interrompt pas le dossier.
 * <p>Hors-ligne : le {@link BcrgSender} est factice, aucun appel réseau.
 */
class DossierIntegrationServiceTest {

    private final List<String> endpointsAppeles = new ArrayList<>();
    private final BcrgSender senderFactice = (uri, body) -> {
        endpointsAppeles.add(uri);
        return ResponseEntity.ok("{\"typeMessageRetour\":\"SUCCES\"}");
    };

    private DossierIntegrationService service() {
        RetryingIntegrationService resilient = new RetryingIntegrationService(
                new IntegrationService(new ExcelReader()),
                new RetryPolicy(2, 1L, 2.0, 2L),
                new InMemoryTransmissionLedger(),
                millis -> { /* pas d'attente en test */ });
        return new DossierIntegrationService(
                new JobRegistries(new SigimfJobRegistry(senderFactice),
                        new SituJobRegistry(senderFactice)), resilient);
    }

    /**
     * Fin de trimestre : elle convient à toutes les périodicités du classeur de test. Le 31/08
     * écarterait M.III.HS_BILAN, qui est trimestrielle — voir l'épreuve dédiée plus bas.
     */
    private TransmissionImf transmission() {
        return new TransmissionFactory().creationForMonth(2026, 9);
    }

    /** Classeur à deux feuilles couvertes (sur les 25 du registre) écrit sur disque. */
    private File classeurTest(Path dossier) throws Exception {
        Path fichier = dossier.resolve("SIGIMF-test.xlsx");
        try (Workbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(fichier)) {
            Sheet hb = wb.createSheet("M.III.HS_BILAN");
            ecrire(hb, 0, "CODE", "N° compte", "LIBELLES", "Montant");
            ecrire(hb, 1, "M.III.HB.901", "90", "Reprise sur autres provisions", "1 000");

            Sheet pfc = wb.createSheet("M.V.PFC");
            ecrire(pfc, 0, "CODE", "ELEMENT", "Montant");
            ecrire(pfc, 1, "M.V.PFC.1", "Encours sain", "250 000");

            wb.write(os);
        }
        return fichier.toFile();
    }

    private void ecrire(Sheet sheet, int r, String... valeurs) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < valeurs.length; c++) row.createCell(c).setCellValue(valeurs[c]);
    }

    @Test
    void tous_traite_les_feuilles_presentes_et_ignore_les_absentes(@TempDir Path dossier) throws Exception {
        DossierReport rapport = service().run(classeurTest(dossier), "SIGIMF", transmission(), List.of("TOUS"));

        assertEquals(2, rapport.total(), "seules les 2 feuilles présentes sont traitées");
        assertEquals(2, rapport.count(Outcome.SUCCES));
        assertTrue(rapport.isComplet());
        assertEquals(List.of("/api/sigimf/m3horsbilan", "/api/sigimf/m5pfc"), endpointsAppeles);
        assertEquals("SIGIMF-test.xlsx", rapport.fichier());
    }

    @Test
    void une_seule_feuille_demandee_n_envoie_qu_elle(@TempDir Path dossier) throws Exception {
        DossierReport rapport = service().run(classeurTest(dossier), "SIGIMF", transmission(),
                List.of("M.V.PFC"));

        assertEquals(1, rapport.total());
        assertEquals(List.of("/api/sigimf/m5pfc"), endpointsAppeles);
    }

    @Test
    void feuille_demandee_mais_absente_du_classeur_est_une_erreur_de_donnees(@TempDir Path dossier)
            throws Exception {
        DossierReport rapport = service().run(classeurTest(dossier), "SIGIMF", transmission(),
                List.of("M.I.BILAN"));

        assertEquals(1, rapport.count(Outcome.ERREUR_DONNEES));
        assertFalse(rapport.isComplet());
        assertTrue(rapport.resultats().get(0).detail().contains("absente du classeur"));
        assertTrue(endpointsAppeles.isEmpty(), "rien ne doit partir");
    }

    @Test
    void feuille_hors_registre_est_signalee_sans_interrompre_le_dossier(@TempDir Path dossier)
            throws Exception {
        // « M.XXIII.INEXISTANTE » n'est dans aucun registre : depuis le câblage de M.IX.SG, SIGIMF
        // est complet et il n'y a plus de feuille réelle non couverte à donner en exemple
        DossierReport rapport = service().run(classeurTest(dossier), "SIGIMF", transmission(),
                List.of("M.XXIII.INEXISTANTE", "M.V.PFC"));

        assertEquals(2, rapport.total());
        assertEquals(1, rapport.count(Outcome.ERREUR_DONNEES));
        assertEquals(1, rapport.count(Outcome.SUCCES));
        assertTrue(rapport.resultats().get(0).detail().contains("non prise en charge"));
        assertEquals(List.of("/api/sigimf/m5pfc"), endpointsAppeles,
                "la feuille suivante est traitée malgré l'erreur précédente");
    }

    /**
     * Cas nominal du workflow Alfresco : la règle documentaire ne sait pas quel fichier
     * réglementaire le validateur vient d'approuver, le classeur le dit par ses feuilles.
     */
    @Test
    void auto_choisit_le_registre_d_apres_les_feuilles_du_classeur(@TempDir Path dossier)
            throws Exception {
        DossierReport rapport = service().run(classeurTest(dossier),
                DossierIntegrationService.CODE_FICHIER_AUTO, transmission(), List.of("TOUS"));

        assertEquals("SIGIMF", rapport.codeFichier());
        assertEquals(List.of("/api/sigimf/m3horsbilan", "/api/sigimf/m5pfc"), endpointsAppeles);
    }

    @Test
    void auto_refuse_un_classeur_hors_perimetre(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("budget.xlsx");
        try (Workbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(fichier)) {
            ecrire(wb.createSheet("Budget 2026"), 0, "CODE", "Montant");
            wb.write(os);
        }

        // erreur de demande (400), pas une panne : le fichier n'est pas un dossier réglementaire
        assertThrows(IllegalArgumentException.class,
                () -> service().run(fichier.toFile(), DossierIntegrationService.CODE_FICHIER_AUTO,
                        transmission(), List.of("TOUS")));
    }

    /**
     * Une feuille ne part que dans son rythme. Sans cette garde, une semestrielle glissée dans un
     * dossier arrêté en mars partirait quand même : la BCRG la refuserait, et un 5xx serait même
     * rejoué quatre fois avant d'abandonner.
     */
    @Test
    void une_feuille_hors_de_son_rythme_n_est_pas_envoyee(@TempDir Path dossier) throws Exception {
        // 31/08/2026 : une fin de mois, mais pas une fin de trimestre
        TransmissionImf aout = new TransmissionFactory().creationForMonth(2026, 8);

        DossierReport rapport = service().run(classeurTest(dossier), "SIGIMF", aout, List.of("TOUS"));

        assertEquals(1, rapport.count(Outcome.HORS_PERIODE), "M.III.HS_BILAN est trimestrielle");
        assertEquals(1, rapport.count(Outcome.SUCCES), "M.V.PFC est mensuelle : elle part");
        assertEquals(List.of("/api/sigimf/m5pfc"), endpointsAppeles,
                "rien n'est envoyé pour la feuille hors période");
        assertTrue(rapport.isComplet(), "hors période n'est pas une erreur : le dossier reste complet");

        String detail = rapport.resultats().stream()
                .filter(r -> r.outcome() == Outcome.HORS_PERIODE).findFirst().orElseThrow().detail();
        assertTrue(detail.contains("2026-09-30"), () -> "le détail doit dire quelle date convient : " + detail);
    }

    @Test
    void un_classeur_illisible_interrompt_tout(@TempDir Path dossier) throws Exception {
        Path faux = dossier.resolve("pas-un-classeur.xlsx");
        Files.writeString(faux, "ceci n'est pas un fichier Excel");

        assertThrows(DossierIntegrationException.class,
                () -> service().run(faux.toFile(), "SIGIMF", transmission(), List.of("TOUS")));
    }
}
