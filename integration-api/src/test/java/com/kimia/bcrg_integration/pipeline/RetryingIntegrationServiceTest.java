package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfHorsBilanString;
import com.kimia.bcrg_integration.client.dto.SigImfHorsBilan;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.mapping.SigImfHorsBilanMapper;
import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valide la résilience (Lot 6) : rejeu sur erreur technique/transitoire, non-rejeu sur erreur
 * fonctionnelle, et idempotence. Hors-ligne : envoi factice stateful + {@link Sleeper} sans attente.
 */
class RetryingIntegrationServiceTest {

    private final IntegrationService integration = new IntegrationService(new ExcelReader());
    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final RetryPolicy fastPolicy = new RetryPolicy(4, 1L, 2.0, 4L);
    private final Sleeper noSleep = millis -> { /* pas d'attente en test */ };

    private Workbook horsBilanWorkbook() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("M.III.HS_BILAN");
        writeRow(s, 0, "CODE", "N° compte", "LIBELLES", "Montant");
        writeRow(s, 1, "M.III.HB.901", "90", "Reprise sur autres provisions", "1 000");
        return wb;
    }

    private void writeRow(Sheet sheet, int r, String... values) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
    }

    private SheetJob<SigImfHorsBilan, DataModelImfSigImfHorsBilanString> job(
            Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender) {
        return new SheetJob<>("M.III.HS_BILAN", new SigImfHorsBilanMapper(),
                (items, t) -> new DataModelImfSigImfHorsBilanString().items(items).transmission(t),
                sender);
    }

    @Test
    void rejoue_sur_503_puis_reussit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender = p -> {
            if (calls.incrementAndGet() <= 2) {
                throw new BcrgApiException(HttpStatus.SERVICE_UNAVAILABLE, null, "service indisponible");
            }
            return ResponseEntity.ok("SUCCES");
        };
        RetryingIntegrationService svc = new RetryingIntegrationService(
                integration, fastPolicy, new InMemoryTransmissionLedger(), noSleep);

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = svc.runResilient(wb, job(sender),
                    transmissions.creationForMonth(2026, 8), "SIGIMF");
            assertEquals(Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(3, calls.get());   // 2 échecs + 1 succès
        }
    }

    @Test
    void idempotence_ignore_le_second_envoi() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender = p -> {
            calls.incrementAndGet();
            return ResponseEntity.ok("SUCCES");
        };
        InMemoryTransmissionLedger ledger = new InMemoryTransmissionLedger();
        RetryingIntegrationService svc = new RetryingIntegrationService(integration, fastPolicy, ledger, noSleep);
        var t = transmissions.creationForMonth(2026, 8);

        try (Workbook wb = horsBilanWorkbook()) {
            var j = job(sender);
            IntegrationResult r1 = svc.runResilient(wb, j, t, "SIGIMF");
            IntegrationResult r2 = svc.runResilient(wb, j, t, "SIGIMF");

            assertEquals(Outcome.SUCCES, r1.outcome());
            assertEquals(Outcome.DEJA_TRANSMIS, r2.outcome());
            assertEquals(1, calls.get());   // un seul envoi réel
        }
    }

    @Test
    void erreur_4xx_non_rejouee() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender = p -> {
            calls.incrementAndGet();
            throw new BcrgApiException(HttpStatus.BAD_REQUEST, null, "rejet fonctionnel");
        };
        RetryingIntegrationService svc = new RetryingIntegrationService(
                integration, fastPolicy, new InMemoryTransmissionLedger(), noSleep);

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = svc.runResilient(wb, job(sender),
                    transmissions.creationForMonth(2026, 8), "SIGIMF");
            assertEquals(Outcome.ERREUR_API, r.outcome());
            assertEquals(400, r.httpStatus());
            assertEquals(1, calls.get());   // aucun rejeu
        }
    }

    @Test
    void cinq_xx_persistant_epuise_les_tentatives() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender = p -> {
            calls.incrementAndGet();
            throw new BcrgApiException(HttpStatus.SERVICE_UNAVAILABLE, null, "5xx persistant");
        };
        RetryingIntegrationService svc = new RetryingIntegrationService(
                integration, fastPolicy, new InMemoryTransmissionLedger(), noSleep);

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = svc.runResilient(wb, job(sender),
                    transmissions.creationForMonth(2026, 8), "SIGIMF");
            assertEquals(Outcome.ERREUR_API, r.outcome());
            assertEquals(4, calls.get());   // maxAttempts atteint
        }
    }

    /**
     * Le gaspillage mesuré en recette : six feuilles refusées par un 500 reproductible, chacune
     * rejouée quatre fois — vingt-quatre appels pour vingt-quatre fois la même réponse.
     * <p>Un 500 dit que le serveur a échoué <em>sur ce contenu</em>. Une seconde chance suffit.
     */
    @Test
    void un_500_reproductible_n_est_tente_que_deux_fois() throws Exception {
        AtomicInteger appels = new AtomicInteger();
        var job = job(p -> {
            appels.incrementAndGet();
            throw new BcrgApiException(HttpStatus.INTERNAL_SERVER_ERROR, null,
                    "{\"status\":500,\"error\":\"Internal Server Error\"}");
        });

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = new RetryingIntegrationService(
                    integration, fastPolicy, new InMemoryTransmissionLedger(), noSleep)
                    .runResilient(wb, job, transmissions.creationForMonth(2026, 3), "SIGIMF");

            assertEquals(Outcome.ERREUR_API, r.outcome());
            assertEquals(2, appels.get(),
                    "une tentative, une seconde chance — et non les 4 de la politique");
        }
    }

    /** Les autres 5xx et les incidents réseau gardent la politique complète : ils sont passagers. */
    @Test
    void les_indisponibilites_gardent_toutes_leurs_tentatives() {
        IntegrationResult cinqCent = IntegrationResult.erreurApi("f", 1, 500, "crash");
        IntegrationResult indisponible = IntegrationResult.erreurApi("f", 1, 503, "indisponible");
        IntegrationResult reseau = IntegrationResult.erreurTechnique("f", 1, "timeout");

        assertEquals(2, RetryingIntegrationService.tentativesAccordees(cinqCent, 4));
        assertEquals(4, RetryingIntegrationService.tentativesAccordees(indisponible, 4));
        assertEquals(4, RetryingIntegrationService.tentativesAccordees(reseau, 4));
        assertEquals(1, RetryingIntegrationService.tentativesAccordees(cinqCent, 1),
                "une politique à une seule tentative reste respectée");
    }
}
