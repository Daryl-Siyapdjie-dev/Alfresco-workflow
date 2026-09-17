package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfDonnePortefeuilleCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Valide le mapping M.V.PFC → {@link SigImfDonnePortefeuilleCredit} sur des lignes propres. */
class SigImfPfcMapperTest {

    private final SigImfPfcMapper mapper = new SigImfPfcMapper();

    private static final List<String> HEADERS = List.of("CODE", "ELEMENT", "Montant");

    private Map<String, String> row(String code, String element, String montant) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("ELEMENT", element);
        m.put("Montant", montant);
        return m;
    }

    @Test
    void mappe_les_lignes() {
        SheetTable sheet = new SheetTable("M.V.PFC", null, HEADERS, List.of(
                row("M.V.PFC.1", "Valeur totale des prêts débloqués", "125 000,50"),
                row("", "Sous-total", "0"),
                row("M.V.PFC.2", "Nombre de crédits en cours", "42")
        ));

        List<SigImfDonnePortefeuilleCredit> items = mapper.map(sheet);

        assertEquals(2, items.size());
        assertEquals("M.V.PFC.1", items.get(0).getCode());
        assertEquals("Valeur totale des prêts débloqués", items.get(0).getElement());
        assertEquals(125000.50, items.get(0).getMontant(), 1e-9);
        assertEquals(42.0, items.get(1).getMontant(), 1e-9);
    }

    @Test
    void rejette_un_montant_non_numerique() {
        SheetTable sheet = new SheetTable("M.V.PFC", null, HEADERS, List.of(
                row("M.V.PFC.1", "Valeur", "n/d")
        ));
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
