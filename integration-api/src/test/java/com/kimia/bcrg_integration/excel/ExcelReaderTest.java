package com.kimia.bcrg_integration.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide la lecture générique d'une feuille SIGIMF (Lot 2), 100 % locale : pas de contexte
 * Spring, pas de Tomcat, pas de réseau. L'échantillon vit dans {@code src/test/resources/samples/}
 * (toujours fiable sur le classpath surefire, contrairement à un CommandLineRunner + spring-boot:run).
 */
class ExcelReaderTest {

    private final ExcelReader reader = new ExcelReader();

    private Workbook openSample() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/SIGIMF.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    /**
     * La date d'arrêté doit pouvoir venir du classeur : dans le workflow Alfresco (dépôt admin →
     * contrôle → validation), aucun acteur ne saisit de période, seule l'approbation déclenche
     * l'envoi. Sans cette lecture, le module ne saurait pas sur quelle période transmettre.
     */
    @Test
    void lit_la_date_des_rapports_du_classeur() throws Exception {
        try (Workbook wb = openSample()) {
            assertEquals(LocalDate.of(2019, 3, 31), reader.dateDesRapports(wb).orElse(null));
        }
    }

    @Test
    void liste_les_feuilles_attendues() throws Exception {
        try (Workbook wb = openSample()) {
            List<String> names = reader.sheetNames(wb);
            assertTrue(names.contains("M.0.INFO.G"), () -> "Feuilles trouvées : " + names);
            assertTrue(names.contains("M.I.BILAN"), () -> "Feuilles trouvées : " + names);
        }
    }

    @Test
    void lit_les_metadonnees_et_les_lignes_de_M0INFOG() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.0.INFO.G");

            System.out.println("[M.0.INFO.G] métadonnées = " + t.metadata());
            System.out.println("[M.0.INFO.G] en-têtes    = " + t.headers());
            System.out.println("[M.0.INFO.G] " + t.rows().size() + " lignes de données");
            t.rows().forEach(r -> System.out.println("    " + r));

            assertNotNull(t.metadata());
            assertFalse(t.headers().isEmpty(), "en-têtes vides");
            assertEquals("CODE", t.headers().get(0).toUpperCase(), "1re colonne d'en-tête attendue = CODE");
            assertFalse(t.rows().isEmpty(), "aucune ligne de données extraite");
            // chaque ligne expose la colonne CODE
            for (Map<String, String> row : t.rows()) {
                assertTrue(row.containsKey(t.headers().get(0)), "ligne sans colonne CODE : " + row);
            }
        }
    }

    @Test
    void lit_le_bilan() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable b = reader.readSheet(wb, "M.I.BILAN");

            System.out.println("[M.I.BILAN] en-têtes = " + b.headers());
            System.out.println("[M.I.BILAN] " + b.rows().size() + " lignes ; 3 premières :");
            b.rows().stream().limit(3).forEach(r -> System.out.println("    " + r));

            assertFalse(b.headers().isEmpty(), "en-têtes vides");
            assertFalse(b.rows().isEmpty(), "aucune ligne de données extraite");
        }
    }

    @Test
    void desambiguise_les_entetes_homonymes_de_M4IMPAYE() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.IV.IMPAYE");

            System.out.println("[M.IV.IMPAYE] en-têtes = " + t.headers());

            // deux colonnes "%" dans la feuille → la 2e doit être suffixée "% (2)"
            assertTrue(t.headers().contains("%"), () -> "en-têtes : " + t.headers());
            assertTrue(t.headers().contains("% (2)"), () -> "en-têtes : " + t.headers());
            // aucune valeur de données perdue par collision : chaque en-tête est unique
            assertEquals(t.headers().size(), t.headers().stream().distinct().count());
        }
    }

    @Test
    void lit_les_listes_M6_M7_avec_marqueur_dentete_parametrable() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable dir = reader.readSheet(wb, "M.VI.ADM.DIR", "N°.");
            SheetTable pers = reader.readSheet(wb, "M.VII.PERSONNEL", "N°.");

            System.out.println("[M.VI.ADM.DIR] en-têtes = " + dir.headers());
            System.out.println("[M.VII.PERSONNEL] en-têtes = " + pers.headers());

            // la ligne d'en-tête « N°. » est bien détectée et ses colonnes métier présentes
            assertTrue(dir.headers().contains("Nom et prénoms"), () -> "en-têtes : " + dir.headers());
            assertTrue(dir.headers().contains("Date d'octroi"), () -> "en-têtes : " + dir.headers());
            assertTrue(pers.headers().contains("Nom et Prénoms"), () -> "en-têtes : " + pers.headers());
            assertTrue(pers.headers().contains("Date originale"), () -> "en-têtes : " + pers.headers());
        }
    }
}
