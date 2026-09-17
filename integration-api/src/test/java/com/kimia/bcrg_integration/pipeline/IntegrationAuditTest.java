package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.audit.AcknowledgementStore;
import com.kimia.bcrg_integration.audit.AuditAction;
import com.kimia.bcrg_integration.audit.AuditEvent;
import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.Auditor;
import com.kimia.bcrg_integration.audit.InMemoryAcknowledgementStore;
import com.kimia.bcrg_integration.audit.InMemoryAuditTrail;
import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfHorsBilanString;
import com.kimia.bcrg_integration.client.dto.SigImfHorsBilan;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import com.kimia.bcrg_integration.mapping.SigImfHorsBilanMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot 7, ticket 7.2 : chaque étape du pipeline laisse une trace exploitable. Ce test vérifie ce qui
 * fait la valeur d'une piste d'audit — qu'on puisse reconstituer <em>après coup</em> pourquoi une
 * feuille est partie, n'est pas partie, ou est partie deux fois — plus le ticket 7.3 (accusés
 * archivés) et le 7.4 (rien de confidentiel n'y entre).
 */
class IntegrationAuditTest {

    private final InMemoryAuditTrail trail = new InMemoryAuditTrail(100);
    private final AcknowledgementStore accuses = new InMemoryAcknowledgementStore();
    private final Auditor auditor = new Auditor(trail, accuses, "compte-service-test");
    private final IntegrationService integration = new IntegrationService(new ExcelReader(), auditor);
    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final RetryPolicy fastPolicy = new RetryPolicy(3, 1L, 2.0, 4L);
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
            String feuille, Function<DataModelImfSigImfHorsBilanString, ResponseEntity<String>> sender) {
        return new SheetJob<>(feuille, new SigImfHorsBilanMapper(),
                (items, t) -> new DataModelImfSigImfHorsBilanString().items(items).transmission(t),
                sender);
    }

    private List<AuditAction> actions() {
        return trail.events().stream().map(AuditEvent::action).toList();
    }

    @Test
    void un_envoi_reussi_trace_transformation_puis_import_et_archive_l_accuse() throws Exception {
        TransmissionImf t = transmissions.creationForMonth(2026, 8);

        try (Workbook wb = horsBilanWorkbook()) {
            integration.run(wb, job("M.III.HS_BILAN", p -> ResponseEntity.ok("{\"typeMessageRetour\":\"SUCCES\"}")),
                    t, "SIGIMF");
        }

        assertEquals(List.of(AuditAction.TRANSFORMATION, AuditAction.IMPORT), actions());
        AuditEvent importe = trail.events().get(1);
        assertEquals(Resultat.SUCCES, importe.resultat());
        assertEquals(200, importe.httpStatus());
        assertEquals("compte-service-test", importe.acteur());
        assertEquals("SIGIMF", importe.cible().codeFichier());
        assertEquals("M.III.HS_BILAN", importe.cible().feuille());

        // ticket 7.3 : l'accusé est retrouvable par sa cible
        AuditTarget cible = new AuditTarget("SIGIMF", "M.III.HS_BILAN", t.getDateArrete());
        assertEquals("{\"typeMessageRetour\":\"SUCCES\"}", accuses.find(cible).orElseThrow().corps());
    }

    @Test
    void une_erreur_de_donnees_trace_l_echec_et_n_archive_aucun_accuse() throws Exception {
        try (Workbook wb = horsBilanWorkbook()) {
            integration.run(wb, job("FEUILLE_INEXISTANTE", p -> ResponseEntity.ok("jamais appelé")),
                    transmissions.creationForMonth(2026, 8), "SIGIMF");
        }

        assertEquals(List.of(AuditAction.TRANSFORMATION), actions());
        assertTrue(trail.events().get(0).isEchec());
        assertTrue(accuses.all().isEmpty(), "aucun envoi n'a eu lieu : pas d'accusé à archiver");
    }

    @Test
    void un_rejeu_est_trace_avant_chaque_nouvelle_tentative() throws Exception {
        AtomicInteger appels = new AtomicInteger();
        RetryingIntegrationService resilient = new RetryingIntegrationService(
                integration, fastPolicy, new InMemoryTransmissionLedger(), noSleep, auditor);

        try (Workbook wb = horsBilanWorkbook()) {
            resilient.runResilient(wb, job("M.III.HS_BILAN", p -> {
                if (appels.incrementAndGet() <= 2) {
                    // 503 : une indisponibilité passagère, qui mérite toutes ses tentatives —
                    // contrairement au 500, qui dit que le serveur échoue sur ce contenu
                    throw new BcrgApiException(HttpStatus.SERVICE_UNAVAILABLE, null, "indisponible");
                }
                return ResponseEntity.ok("SUCCES");
            }), transmissions.creationForMonth(2026, 8), "SIGIMF");
        }

        assertEquals(2, actions().stream().filter(a -> a == AuditAction.REPRISE).count(),
                "deux échecs transitoires = deux décisions de rejeu tracées");
        assertEquals(3, appels.get());
    }

    @Test
    void un_envoi_ignore_par_idempotence_est_trace_comme_tel() throws Exception {
        InMemoryTransmissionLedger ledger = new InMemoryTransmissionLedger();
        RetryingIntegrationService resilient = new RetryingIntegrationService(
                integration, fastPolicy, ledger, noSleep, auditor);
        TransmissionImf t = transmissions.creationForMonth(2026, 8);
        var j = job("M.III.HS_BILAN", p -> ResponseEntity.ok("SUCCES"));

        try (Workbook wb = horsBilanWorkbook()) {
            resilient.runResilient(wb, j, t, "SIGIMF");
            resilient.runResilient(wb, j, t, "SIGIMF");
        }

        List<AuditEvent> idempotence = trail.events().stream()
                .filter(e -> e.action() == AuditAction.IDEMPOTENCE).toList();
        assertEquals(1, idempotence.size());
        assertEquals(Resultat.IGNORE, idempotence.get(0).resultat());
        // le second passage ne rejoue ni la transformation ni l'import
        assertEquals(1, actions().stream().filter(a -> a == AuditAction.IMPORT).count());
    }

    @Test
    void un_accuse_contenant_un_secret_est_archive_masque() throws Exception {
        TransmissionImf t = transmissions.creationForMonth(2026, 8);

        try (Workbook wb = horsBilanWorkbook()) {
            integration.run(wb, job("M.III.HS_BILAN",
                    p -> ResponseEntity.ok("{\"statut\":\"OK\",\"token\":\"eyJhbGciOiJIUzI1NiJ9.a.b\"}")),
                    t, "SIGIMF");
        }

        String corps = accuses.find(new AuditTarget("SIGIMF", "M.III.HS_BILAN", t.getDateArrete()))
                .orElseThrow().corps();
        assertFalse(corps.contains("eyJ"), "le jeton ne doit pas être archivé : " + corps);
        assertTrue(corps.contains("\"statut\":\"OK\""), "le reste de l'accusé doit rester lisible");
    }
}
