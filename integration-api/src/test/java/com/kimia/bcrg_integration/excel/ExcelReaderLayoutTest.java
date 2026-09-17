package com.kimia.bcrg_integration.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide les trois capacités ajoutées au lecteur pour les feuilles <strong>matricielles</strong>,
 * sur le fichier réel : lignes vides internes tolérées, en-têtes répétés d'un bloc à l'autre ignorés,
 * et en-tête réparti sur deux lignes. Comme {@link ExcelReaderTest}, 100 % local (pas de Spring,
 * pas de réseau) ; on ne vérifie ici que la <em>structure</em> lue, pas les valeurs — celles du
 * template sont des formules Excel (cf. mappers, testés sur données propres).
 */
class ExcelReaderLayoutTest {

    private final ExcelReader reader = new ExcelReader();

    private Workbook openSample() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/SIGIMF.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    private List<String> codes(SheetTable t) {
        return t.rows().stream().map(r -> r.get("CODE")).filter(c -> c != null && !c.isEmpty()).toList();
    }

    @Test
    void s_arrete_a_la_premiere_ligne_vide_par_defaut() throws Exception {
        try (Workbook wb = openSample()) {
            // M.XVII.PROV : une ligne vide sépare la dernière ligne (ratio) du reste du tableau
            SheetTable strict = reader.readSheet(wb, "M.XVII.PROV");
            SheetTable aeree = reader.readSheet(wb, "M.XVII.PROV", SheetLayout.defaults().skippingBlankRows());

            assertEquals(7, codes(strict).size(), "comportement historique : arrêt à la 1re ligne vide");
            assertEquals(8, codes(aeree).size(), "la ligne de ratio d'après la ligne vide est récupérée");
            assertTrue(codes(aeree).contains("M.XX.PROV.11"), () -> "codes lus : " + codes(aeree));
        }
    }

    @Test
    void lit_les_groupes_separes_par_des_lignes_vides_de_M11RPCS() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.XI.RPCS", SheetLayout.defaults().skippingBlankRows());

            System.out.println("[M.XI.RPCS] en-têtes = " + t.headers());
            assertEquals(17, codes(t).size(), () -> "codes lus : " + codes(t));
            assertTrue(codes(t).contains("M.XI.RPCS.1"));
            assertTrue(codes(t).contains("M.XI.RPCS.17"), "le dernier groupe (après 2 lignes vides) est lu");
            // les colonnes sectorielles alignées à coups d'espaces restent des en-têtes distincts
            assertEquals(9, t.headers().size(), () -> "en-têtes : " + t.headers());
        }
    }

    @Test
    void enchaine_les_quatre_blocs_de_M21_en_ignorant_les_entetes_repetes() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.XXI.BALANCES AGEES",
                    SheetLayout.defaults().skippingBlankRows());

            // 4 blocs × 9 lignes = 36 codes, les 3 en-têtes « CODE » suivants n'étant pas des données
            assertEquals(36, codes(t).size(), () -> "codes lus : " + codes(t));
            assertTrue(codes(t).contains("M.XXI.BAL.1"), "bloc A");
            assertTrue(codes(t).contains("M.XXI.BAL.36"), "bloc D");
            assertFalse(t.rows().stream().anyMatch(r -> "CODE".equals(r.get("CODE"))),
                    "aucune ligne d'en-tête répétée ne doit être prise pour une donnée");
            // les deux colonnes « % » du bloc restent distinctes
            assertTrue(t.headers().contains("%") && t.headers().contains("% (2)"),
                    () -> "en-têtes : " + t.headers());
        }
    }

    @Test
    void trouve_le_bloc_geographique_de_M15RECAP_dont_l_entete_commence_en_2e_colonne() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable principal = reader.readSheet(wb, "M.XV.RECAP");
            SheetTable geo = reader.readSheet(wb, "M.XV.RECAP",
                    SheetLayout.marker("Couverture Géographique"));

            // le bloc principal s'arrête avant la ligne vide qui précède le tableau géographique
            assertEquals(46, codes(principal).size(), () -> "codes lus : " + codes(principal));

            // le second bloc a sa propre ligne d'en-tête, décalée d'une colonne
            assertEquals("", geo.headers().get(0), "la colonne des codes n'a pas d'intitulé");
            assertTrue(geo.headers().contains("Conakry"), () -> "en-têtes : " + geo.headers());
            assertTrue(geo.headers().contains("Guinée Forestière"), () -> "en-têtes : " + geo.headers());
            assertEquals(6, geo.rows().size(), "les 6 lignes M.XVIII.RECAP.47 à .52");
            assertEquals("M.XVIII.RECAP.47", geo.rows().get(0).get("col0"));
        }
    }

    @Test
    void compose_l_entete_sur_deux_lignes_de_M12RCS() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.XII.RCS", SheetLayout.defaults().spanning(2));

            System.out.println("[M.XII.RCS] en-têtes = " + t.headers());
            // l'intitulé précis de la 2e ligne l'emporte sur le chapeau fusionné « RECOUVREMENT… »
            assertTrue(t.headers().contains("1er trimestre"), () -> "en-têtes : " + t.headers());
            assertTrue(t.headers().contains("4ème trimestre"), () -> "en-têtes : " + t.headers());
            assertTrue(t.headers().contains("Tranches de jours de retard"), () -> "en-têtes : " + t.headers());
            assertFalse(t.headers().contains("RECOUVREMENT AU COURS DE L'EXERCICE"),
                    () -> "le chapeau fusionné ne doit pas rester comme nom de colonne : " + t.headers());
            assertEquals(5, codes(t).size(), () -> "codes lus : " + codes(t));

            // la 3e ligne d'en-tête (« Principal » répété) est lue comme une ligne sans code
            Map<String, String> premiere = t.rows().get(0);
            assertEquals("", premiere.get("CODE"));
        }
    }
}
