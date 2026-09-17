package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRapportIndicateur;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valide le mapper mutualisé des quatre feuilles d'indicateurs (M.XXII.INDIC.PRUDENTIELS,
 * M.XXII.RATIO.PRUDENTIELS, TAB_AUTRES_INDIC_1 et _2), dont la 3e colonne s'intitule tantôt
 * {@code FORMULES}, tantôt {@code NORMES}, et dont la ligne de pied « Total » n'est pas une donnée.
 */
class SigImfRapportIndicateurMapperTest {

    private final SigImfRapportIndicateurMapper mapper = new SigImfRapportIndicateurMapper();

    private static final String NUM = "N°.";
    private static final String RATIOS = "RATIOS / VALEURS";

    private Map<String, String> row(String troisieme, String numero, String libelle,
                                    String valeurTroisieme, String ratio) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NUM, numero);
        m.put("LIBELLE", libelle);
        m.put(troisieme, valeurTroisieme);
        m.put(RATIOS, ratio);
        return m;
    }

    @Test
    void mappe_la_feuille_a_colonne_formules() {
        List<String> headers = List.of(NUM, "LIBELLE", "FORMULES", RATIOS);
        SheetTable sheet = new SheetTable("M.XXII.INDIC.PRUDENTIELS", null, headers, List.of(
                row("FORMULES", "M.IND.IM.2", "TAUX D'EXPOSITION",
                        "CREDITS EN SOUFFRANCE NETS/FONDS PROPRES NETS", "12,5"),
                row("FORMULES", "Total", "N/A", "N/A", "N/A")
        ));

        List<SigImfRapportIndicateur> items = mapper.map(sheet);

        assertEquals(1, items.size(), "la ligne de pied « Total » est ignorée");
        SigImfRapportIndicateur i = items.get(0);
        assertEquals("M.IND.IM.2", i.getnCompte());
        assertEquals("TAUX D'EXPOSITION", i.getLibelle());
        assertEquals("CREDITS EN SOUFFRANCE NETS/FONDS PROPRES NETS", i.getFormules());
        assertEquals(12.5, i.getMontant(), 1e-9);
    }

    @Test
    void mappe_la_feuille_a_colonne_normes() {
        List<String> headers = List.of(NUM, "LIBELLE", "NORMES", RATIOS);
        SheetTable sheet = new SheetTable("TAB_AUTRES_INDIC_1", null, headers, List.of(
                row("NORMES", "M.AI1.IM.1", "FONDS PROPRES NETS (FPN)", "≥ 15%", "1 250 000,50")
        ));

        List<SigImfRapportIndicateur> items = mapper.map(sheet);

        assertEquals(1, items.size());
        assertEquals("M.AI1.IM.1", items.get(0).getnCompte());
        assertEquals("≥ 15%", items.get(0).getFormules(), "à défaut de champ « norme » côté DTO");
        assertEquals(1_250_000.50, items.get(0).getMontant(), 1e-9);
    }

    @Test
    void exige_la_colonne_des_ratios() {
        SheetTable sheet = new SheetTable("TAB_AUTRES_INDIC_2", null, List.of(NUM, "LIBELLE"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
