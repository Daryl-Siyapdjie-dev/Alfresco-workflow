package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfHorsBilanString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfRecapString;
import com.kimia.bcrg_integration.client.dto.SigImfRecap;
import com.kimia.bcrg_integration.excel.ExcelReader;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide le <strong>câblage réel</strong> des jobs SIGIMF : bonne feuille → bon endpoint → bon
 * {@code DataModel}, le tout sans réseau (le {@link BcrgSender} est une lambda qui capture l'appel).
 */
class SigimfJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());

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

    @Test
    void connait_les_26_feuilles_sigimf() {
        SigimfJobRegistry registry = new SigimfJobRegistry((uri, body) -> ResponseEntity.ok("x"));
        assertEquals(26, registry.sheets().size(), "SIGIMF est complet depuis le câblage de M.IX.SG");
        assertTrue(registry.sheets().contains("M.0.INFO.G"));
        assertTrue(registry.sheets().contains("M.XV.RECAP"));
        assertTrue(registry.sheets().contains("M.XXI.BALANCES AGEES"));
        assertTrue(registry.sheets().contains("TAB_AUTRES_INDIC_2"));
        assertNotNull(registry.forSheet("M.IX.SG"), "la dernière feuille est câblée");
    }

    @Test
    void decrit_la_forme_des_feuilles_matricielles() {
        SigimfJobRegistry registry = new SigimfJobRegistry((uri, body) -> ResponseEntity.ok("x"));

        // en-tête sur 2 lignes (chapeau fusionné « RECOUVREMENT… » précisé par « 1er trimestre »)
        assertEquals(2, registry.forSheet("M.XII.RCS").blocks().get(0).layout().headerRowSpan());
        // 4 blocs de même forme séparés par des lignes vides et des en-têtes répétés
        assertTrue(registry.forSheet("M.XXI.BALANCES AGEES").blocks().get(0).layout().skipBlankRows());
        // feuilles d'indicateurs : marqueur « N°. », agrégats hors tableau laissés de côté
        assertEquals("N°.", registry.forSheet("TAB_AUTRES_INDIC_1").blocks().get(0).layout().headerMarker());
        assertFalse(registry.forSheet("TAB_AUTRES_INDIC_1").blocks().get(0).layout().skipBlankRows());
        // feuille standard : un en-tête sur une ligne, une seule tranche à lire
        assertEquals(1, registry.forSheet("M.III.HS_BILAN").blocks().get(0).layout().headerRowSpan());
        assertFalse(registry.forSheet("M.III.HS_BILAN").blocks().get(0).layout().skipBlankRows());
        assertEquals(1, registry.forSheet("M.III.HS_BILAN").blocks().size());
        // M.I.BILAN empile actif et passif : deux blocs, le second sur la 2e occurrence de « CODE »
        assertEquals(2, registry.forSheet("M.I.BILAN").blocks().size());
        assertEquals(2, registry.forSheet("M.I.BILAN").blocks().get(1).layout().occurrence());
    }

    @Test
    void le_job_recap_lit_ses_deux_blocs_dans_un_seul_envoi() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        SigimfJobRegistry registry = new SigimfJobRegistry((u, b) -> {
            body.set(b);
            return ResponseEntity.ok("{}");
        });
        assertEquals(2, registry.forSheet("M.XV.RECAP").blocks().size());

        try (Workbook wb = recapWorkbook()) {
            IntegrationResult r = service.run(wb, registry.forSheet("M.XV.RECAP"),
                    transmissions.creationForMonth(2026, 8));

            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(2, r.itemCount(), "1 ligne du récapitulatif + 1 ligne de couverture géographique");
            DataModelImfSigImfRecapString payload =
                    assertInstanceOf(DataModelImfSigImfRecapString.class, body.get());
            List<SigImfRecap> items = payload.getItems();
            assertEquals(3.0, items.get(0).getValeur(), 1e-9);
            assertEquals("Conakry et régions", items.get(1).getCouvertureGeographique());
            assertEquals(15.0, items.get(1).getTotal(), 1e-9);
        }
    }

    /** Feuille M.XV.RECAP réduite : le récapitulatif, une ligne vide, puis le tableau géographique. */
    private Workbook recapWorkbook() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("M.XV.RECAP");
        writeRow(s, 0, "CODE", "ELEMENT", "", "VALEUR");
        writeRow(s, 1, "M.XVIII.RECAP.1", "Nombre d'agences", "", "3");
        // ligne 2 laissée vide : elle sépare les deux blocs
        writeRow(s, 3, "", "Couverture Géographique", "Conakry", "Basse Guinée", "Moyenne Guinée",
                "Haute Guinée", "Guinée Forestière", "Total");
        writeRow(s, 4, "M.XVIII.RECAP.47", "Conakry et régions", "1", "2", "3", "4", "5", "15");
        return wb;
    }

    @Test
    void le_job_horsbilan_poste_le_bon_datamodel_sur_le_bon_endpoint() throws Exception {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<Object> body = new AtomicReference<>();
        BcrgSender fake = (u, b) -> {
            uri.set(u);
            body.set(b);
            return ResponseEntity.ok("{\"typeMessageRetour\":\"SUCCES\"}");
        };
        SigimfJobRegistry registry = new SigimfJobRegistry(fake);

        try (Workbook wb = horsBilanWorkbook()) {
            IntegrationResult r = service.run(wb, registry.forSheet("M.III.HS_BILAN"),
                    transmissions.creationForMonth(2019, 3));

            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(1, r.itemCount());
            assertEquals("/api/sigimf/m3horsbilan", uri.get());
            assertNotNull(body.get());
            DataModelImfSigImfHorsBilanString payload =
                    assertInstanceOf(DataModelImfSigImfHorsBilanString.class, body.get());
            assertEquals(1, payload.getItems().size());
            assertNotNull(payload.getTransmission().getDateArrete());
        }
    }

    /**
     * SIRYF refuse un corps portant {@code "items2": []} — « Veuillez fournir les donnees a
     * traiter » — alors que le meme corps sans ce champ est accepte (verifie en reel le
     * 26/08/2026). Or le generateur OpenAPI initialise ce champ, artefact de la signature
     * generique {@code DataModel<Item, String>}, a une liste vide : l'assembleur doit le vider.
     */
    @Test
    void le_payload_ne_porte_pas_de_liste_items2_vide() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        SigimfJobRegistry registry = new SigimfJobRegistry((u, b) -> {
            body.set(b);
            return ResponseEntity.ok("Traitement effectue avec succes");
        });

        try (Workbook wb = horsBilanWorkbook()) {
            service.run(wb, registry.forSheet("M.III.HS_BILAN"), transmissions.creationForMonth(2026, 3));

            DataModelImfSigImfHorsBilanString payload =
                    assertInstanceOf(DataModelImfSigImfHorsBilanString.class, body.get());
            assertNull(payload.getItems2(), "items2 doit partir a null, jamais en liste vide");
            assertEquals(1, payload.getItems().size(), "les vrais items restent intacts");
        }
    }
}
