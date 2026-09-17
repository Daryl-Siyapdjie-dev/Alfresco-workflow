package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRecap;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valide le mapping du bloc « Couverture Géographique » de M.XV.RECAP → {@link SigImfRecap}.
 * Les en-têtes reproduisent ceux du fichier réel, dont la 1re colonne (celle des codes) est
 * <strong>sans intitulé</strong> — le lecteur la nomme alors « col0 ».
 */
class SigImfRecapGeoMapperTest {

    private final SigImfRecapGeoMapper mapper = new SigImfRecapGeoMapper();

    private static final List<String> HEADERS = List.of("", "Couverture  Géographique ", "Conakry",
            "Basse Guinée", "Moyenne Guinée", "Haute Guinée", "Guinée Forestière", "Total");

    private Map<String, String> row(String code, String libelle, String... regions) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("col0", code);                       // en-tête vide → clé positionnelle
        m.put("Couverture  Géographique ", libelle);
        String[] cols = {"Conakry", "Basse Guinée", "Moyenne Guinée", "Haute Guinée", "Guinée Forestière", "Total"};
        for (int i = 0; i < cols.length; i++) m.put(cols[i], i < regions.length ? regions[i] : "");
        return m;
    }

    @Test
    void mappe_les_lignes_de_couverture_geographique() {
        SheetTable sheet = new SheetTable("M.XV.RECAP", null, HEADERS, List.of(
                row("M.XVIII.RECAP.47", "Nombre total de point de couverture des services",
                        "1", "2", "3", "4", "5", "15"),
                row("", "ligne sans code", "1", "1", "1", "1", "1", "5"),
                row("M.XVIII.RECAP.52", "Volume de financement des crédits", "", "", "", "", "", "0")
        ));

        List<SigImfRecap> items = mapper.map(sheet);

        assertEquals(2, items.size());
        SigImfRecap g = items.get(0);
        assertEquals("M.XVIII.RECAP.47", g.getCode());
        assertEquals("Nombre total de point de couverture des services", g.getCouvertureGeographique());
        assertEquals(1.0, g.getConakry(), 1e-9);
        assertEquals(2.0, g.getBasseGuinee(), 1e-9);
        assertEquals(3.0, g.getMoyenneGuinee(), 1e-9);
        assertEquals(4.0, g.getHauteGuinee(), 1e-9);
        assertEquals(5.0, g.getGuineeForestiere(), 1e-9);
        assertEquals(15.0, g.getTotal(), 1e-9);
        assertNull(g.getValeur(), "le champ du bloc principal reste vide ici");

        assertNull(items.get(1).getConakry(), "région non renseignée → null");
    }

    @Test
    void exige_les_colonnes_regionales() {
        SheetTable sheet = new SheetTable("M.XV.RECAP", null,
                List.of("", "Couverture Géographique", "Conakry"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
