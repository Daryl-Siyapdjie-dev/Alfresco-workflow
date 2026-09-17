package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCreancierPlusImportant;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code M.XVIII.DIX.CREANCIERS} → {@link SigImfCreancierPlusImportant}.
 *
 * <p>Ce test est né faux : il inventait ses en-têtes (« Entreprise individuelle ou personnes
 * morales », « Nombre d'employé »), et le mapper les attendait — chacun confirmait l'autre. La
 * recette réelle du 27/08/2026 a tranché : SIRYF a refusé la feuille sur
 * <em>« Colonne obligatoire absente »</em>, et le template porte en fait
 * {@code Nature de Ressource} et {@code Date d'échéance}.
 * <p>D'où la première épreuve ci-dessous, qui lit le <strong>vrai template</strong> : un mapper ne
 * peut plus se valider contre des colonnes qu'il a lui-même imaginées.
 */
class SigImfCreancierImportantMapperTest {

    private final SigImfCreancierImportantMapper mapper = new SigImfCreancierImportantMapper();

    /** Les intitulés réels, relevés sur {@code templates/SIGIMF.xlsx}. */
    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et prénoms", "Nature de Ressource", "Date originale",
            "Montant original", "Solde", "Date d'échéance");

    @Test
    void lit_les_colonnes_du_vrai_template() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/SIGIMF.xlsx absent du classpath de test");
        try (Workbook wb = WorkbookFactory.create(in)) {
            SheetTable feuille = new ExcelReader().readSheet(wb, "M.XVIII.DIX.CREANCIERS",
                    SheetLayout.marker(SigImfCreancierImportantMapper.HEADER_MARKER));

            // aucune colonne obligatoire absente : c'est ce qui manquait, et le template l'aurait dit
            List<SigImfCreancierPlusImportant> items = mapper.map(feuille);
            assertEquals(10, items.size(), "dix créanciers ; la ligne « TOTAL » n'en est pas un");
            assertTrue(items.stream().allMatch(i -> i.getNumero() != null));
            assertEquals(1, items.get(0).getNumero());
            assertEquals(10, items.get(9).getNumero());
        }
    }

    @Test
    void mappe_les_champs_correspondants() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("N°.", "1");
        r.put("Nom et prénoms", "Coopérative Beta");
        r.put("Nature de Ressource", "Personne morale");
        r.put("Date originale", "10/10/2018");
        r.put("Montant original", "80 000");
        r.put("Solde", "60 000");
        r.put("Date d'échéance", "10/10/2028");

        List<SigImfCreancierPlusImportant> items =
                mapper.map(new SheetTable("M.XVIII.DIX.CREANCIERS", null, HEADERS, List.of(r)));

        assertEquals(1, items.size());
        SigImfCreancierPlusImportant it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("Coopérative Beta", it.getNomEtPrenoms());
        assertEquals(OffsetDateTime.of(2018, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateOriginale());
        assertEquals(80000.0, it.getMontantOriginal(), 1e-9);
        assertEquals(60000.0, it.getSolde(), 1e-9);
        assertEquals("Personne morale", it.getNatureDeRessource());
        // la date d'échéance avait été classée « sans source » : elle a bien une colonne
        assertEquals(OffsetDateTime.of(2028, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateEcheance());
    }

    /** La ligne de clôture des listes (« TOTAL | N/A | N/A… ») n'est pas un enregistrement. */
    @Test
    void ecarte_la_ligne_de_total_dont_les_cellules_ne_sont_pas_vides() {
        Map<String, String> total = new LinkedHashMap<>();
        total.put("N°.", "TOTAL");
        total.put("Nom et prénoms", "N/A");
        total.put("Nature de Ressource", "N/A");
        total.put("Date originale", "N/A");
        total.put("Montant original", "0");
        total.put("Solde", "0");
        total.put("Date d'échéance", "N/A");

        assertEquals(List.of(), mapper.map(
                new SheetTable("M.XVIII.DIX.CREANCIERS", null, HEADERS, List.of(total))));
    }
}
