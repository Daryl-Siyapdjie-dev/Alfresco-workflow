package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfHorsBilanString;
import com.kimia.bcrg_integration.client.dto.SigImfHorsBilan;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.mapping.SigImfHorsBilanMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

//import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide l'orchestration bout-en-bout (lecture → mapping → assemblage → envoi), <strong>sans réseau</strong>
 * (le {@code sender} du job est une lambda factice) et de façon <strong>déterministe</strong> : on
 * construit un petit classeur M.III.HS_BILAN en mémoire (le vrai template contient plus bas des
 * montants non numériques, ce qui relève d'un autre test, celui du mapping).
 */
class IntegrationServiceTest {

    private final IntegrationService service = new IntegrationService(new ExcelReader());
    private final TransmissionFactory transmissions = new TransmissionFactory();

    /** Classeur minimal conforme : ligne d'en-tête (1re cellule « CODE ») + 2 lignes propres. */
    private Workbook horsBilanWorkbook() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("M.III.HS_BILAN");
        writeRow(s, 0, "CODE", "N° compte", "LIBELLES", "Montant");
        writeRow(s, 1, "M.III.HB.901", "90", "Reprise sur autres provisions", "1 000");
        writeRow(s, 2, "M.III.HB.902", "91", "Autre engagement", "0");
        return wb;
    }

    private void writeRow(Sheet sheet, int r, String... values) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
    }

    private SheetJob<SigImfHorsBilan, DataModelImfSigImfHorsBilanString> horsBilanJob(
            java.util.function.Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender) {
        return new SheetJob<>(
                "M.III.HS_BILAN",
                new SigImfHorsBilanMapper(),
                (items, t) -> new DataModelImfSigImfHorsBilanString().items(items).transmission(t),
                sender);
    }

    @Test
    void succes_bout_en_bout_avec_payload_assemble() throws Exception {
        AtomicReference<DataModelImfSigImfHorsBilanString> envoye = new AtomicReference<>();
        var job = horsBilanJob(payload -> {
            envoye.set(payload);
            return ResponseEntity.ok("{\"typeMessageRetour\":\"SUCCES\"}");
        });

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = service.run(wb, job, transmissions.creationForMonth(2019, 3));

            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(2, r.itemCount());
            assertEquals(200, r.httpStatus());

            DataModelImfSigImfHorsBilanString p = envoye.get();
            assertNotNull(p);
            assertEquals(2, p.getItems().size());
            assertEquals("M.III.HB.901", p.getItems().get(0).getCode());
            assertEquals(1000.0, p.getItems().get(0).getMontant(), 1e-9);
            assertNotNull(p.getTransmission().getDateArrete());
        }
    }

    @Test
    void feuille_absente_donne_ERREUR_DONNEES_sans_envoi() throws Exception {
        AtomicReference<Boolean> appele = new AtomicReference<>(false);
        var job = new SheetJob<>(
                "FEUILLE_INEXISTANTE",
                new SigImfHorsBilanMapper(),
                (items, t) -> new DataModelImfSigImfHorsBilanString().items(items).transmission(t),
                payload -> { appele.set(true); return ResponseEntity.ok("x"); });

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = service.run(wb, job, transmissions.creationForMonth(2019, 3));

            assertEquals(IntegrationResult.Outcome.ERREUR_DONNEES, r.outcome());
            assertFalse(appele.get(), "aucun envoi ne doit avoir lieu");
        }
    }

    @Test
    void erreur_api_est_classee_ERREUR_API() throws Exception {
        var job = horsBilanJob(payload -> {
            throw new BcrgApiException(HttpStatus.BAD_REQUEST, null, "rejet fonctionnel");
        });

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = service.run(wb, job, transmissions.creationForMonth(2019, 3));

            assertEquals(IntegrationResult.Outcome.ERREUR_API, r.outcome());
            assertEquals(400, r.httpStatus());
            assertTrue(r.itemCount() > 0, "les items ont été mappés avant l'échec d'envoi");
        }
    }

    /**
     * Le piège que cette épreuve verrouille : SIRYF répond 200 « Traitement effectué avec succès »
     * <em>et</em> refuse des lignes dans le même corps. Classer cela en {@code SUCCES} annoncerait
     * un envoi complet là où la donnée est partie amputée — et la période, elle, est consommée.
     */
    @Test
    void un_200_porteur_d_erreurs_de_coherence_n_est_pas_un_succes() throws Exception {
        String corpsAvecErreurs = "{\"erreurs\":[{\"ligne\":\"4\",\"erreur\":\" Code X est introuvable \"}],"
                + "\"description\":\"Traitement effectué avec succès\",\"statutCode\":\"OK\"}";

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = service.run(wb,
                    horsBilanJob(payload -> ResponseEntity.ok(corpsAvecErreurs)),
                    transmissions.creationForMonth(2026, 3));

            assertEquals(IntegrationResult.Outcome.ACCEPTE_AVEC_ERREURS, r.outcome());
            assertFalse(r.isSucces(), "ce n'est pas un succès");
            assertTrue(r.estTransmis(), "la transmission a bien eu lieu : la période est consommée");
            assertTrue(r.detail().contains("Code X est introuvable"), r.detail());
            assertEquals(200, r.httpStatus());
        }
    }
}
