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
 * Valide le mapping M.XV.RECAP → {@link SigImfRecap}, feuille à <strong>structure irrégulière</strong> :
 * lignes de section intercalées (« INFORMATIONS GENERALES », …) à ignorer, sous-postes sans valeur,
 * et colonne C vide entre {@code ELEMENT} et {@code VALEUR} (reproduite ici comme dans le fichier réel).
 */
class SigImfRecapMapperTest {

    private final SigImfRecapMapper mapper = new SigImfRecapMapper();

    /** En-têtes du fichier réel : la colonne C est vide → en-tête « » (clé de ligne « col2 »). */
    private static final List<String> HEADERS = List.of("CODE", "ELEMENT", "", "VALEUR");

    private Map<String, String> row(String code, String element, String valeur) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("ELEMENT", element);
        m.put("col2", "");
        m.put("VALEUR", valeur);
        return m;
    }

    @Test
    void mappe_les_lignes_et_ignore_les_sections() {
        SheetTable sheet = new SheetTable("M.XV.RECAP", null, HEADERS, List.of(
                row("", "INFORMATIONS GENERALES", ""),                                  // ligne de section
                row("M.XVIII.RECAP.1", "Nombre total de point de couverture des services", "3"),
                row("M.XVIII.RECAP.5", "CDI", ""),                                      // sous-poste non renseigné
                row("", "PORTEFEUILLE DE PRETS", ""),                                   // ligne de section
                row("M.XVIII.RECAP.20", "Répartition du Volume de financement", "1 250 000,75")
        ));

        List<SigImfRecap> items = mapper.map(sheet);

        assertEquals(3, items.size());
        assertEquals("M.XVIII.RECAP.1", items.get(0).getCode());
        assertEquals("Nombre total de point de couverture des services", items.get(0).getElement());
        assertEquals(3.0, items.get(0).getValeur(), 1e-9);
        assertNull(items.get(1).getValeur(), "sous-poste sans valeur → null");
        assertEquals(1250000.75, items.get(2).getValeur(), 1e-9);
    }

    @Test
    void rejette_une_valeur_non_numerique() {
        SheetTable sheet = new SheetTable("M.XV.RECAP", null, HEADERS, List.of(
                row("M.XVIII.RECAP.1", "Nombre d'agences", "n/d")
        ));
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }

    @Test
    void exige_la_colonne_valeur() {
        SheetTable sheet = new SheetTable("M.XV.RECAP", null, List.of("CODE", "ELEMENT"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
