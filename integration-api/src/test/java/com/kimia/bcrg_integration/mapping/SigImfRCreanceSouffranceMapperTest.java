package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRCreanceSouffrance;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valide le mapping M.XII.RCS → {@link SigImfRCreanceSouffrance}. Les en-têtes sont ceux que produit
 * le lecteur après composition des deux lignes d'en-tête de la feuille.
 */
class SigImfRCreanceSouffranceMapperTest {

    private final SigImfRCreanceSouffranceMapper mapper = new SigImfRCreanceSouffranceMapper();

    private static final String TRANCHE = "Tranches de jours de retard";
    private static final String SOLDE = "Solde des crédits au 31 décembre précédent";

    private static final List<String> HEADERS = List.of("CODE", TRANCHE, SOLDE,
            "1er trimestre", "2ème trimestre", "3ème trimestre", "4ème trimestre", "TOTAL");

    private Map<String, String> row(String code, String tranche, String... montants) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put(TRANCHE, tranche);
        String[] cols = {SOLDE, "1er trimestre", "2ème trimestre", "3ème trimestre", "4ème trimestre", "TOTAL"};
        for (int i = 0; i < cols.length; i++) m.put(cols[i], i < montants.length ? montants[i] : "");
        return m;
    }

    @Test
    void mappe_les_tranches_et_les_trimestres() {
        SheetTable sheet = new SheetTable("M.XII.RCS", null, HEADERS, List.of(
                row("", "", "Principal", "Principal", "Principal", "Principal", "Principal", "Principal"),
                row("M.XII.RCS.1", "(91 à 180 jours)", "10 000", "1 000", "2 000", "3 000", "4 000", "10 000"),
                row("M.XII.RCS.4", "(plus de 720 jours)", "5 000", "", "", "", "", "0")
        ));

        List<SigImfRCreanceSouffrance> items = mapper.map(sheet);

        assertEquals(2, items.size(), "la 3e ligne d'en-tête (« Principal ») n'est pas une donnée");
        SigImfRCreanceSouffrance r = items.get(0);
        assertEquals("M.XII.RCS.1", r.getCode());
        assertEquals("(91 à 180 jours)", r.getTrancheJourRetard());
        assertEquals(10_000.0, r.getSoldeCreditPrecedent(), 1e-9);
        assertEquals(1_000.0, r.getTrimestre1(), 1e-9);
        assertEquals(2_000.0, r.getTrimestre2(), 1e-9);
        assertEquals(3_000.0, r.getTrimestre3(), 1e-9);
        assertEquals(4_000.0, r.getTrimestre4(), 1e-9);
        assertEquals(10_000.0, r.getTotal(), 1e-9);

        assertNull(items.get(1).getTrimestre1(), "trimestre non renseigné → null");
    }

    @Test
    void exige_les_colonnes_de_trimestre() {
        SheetTable sheet = new SheetTable("M.XII.RCS", null, List.of("CODE", TRANCHE, SOLDE), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
