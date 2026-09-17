package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSituationImpayeCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valide le mapping M.IV.IMPAYE → {@link SigImfSituationImpayeCredit}. Les en-têtes reproduisent la
 * sortie du lecteur pour cette feuille : les deux colonnes « % » homonymes sont désambiguïsées en
 * {@code "%"} et {@code "% (2)"}.
 */
class SigImfImpayeMapperTest {

    private final SigImfImpayeMapper mapper = new SigImfImpayeMapper();

    private static final List<String> HEADERS = List.of(
            "CODE", "ELEMENT", "Nombres de prêts", "%", "Capital restant dû", "% (2)",
            "Montant des dépôts nantis ou en garantie", "% du capital couvert");

    @Test
    void mappe_les_huit_colonnes_avec_les_deux_taux_distincts() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("CODE", "M.IV.IMP.2");
        r.put("ELEMENT", "IX");
        r.put("Nombres de prêts", "10");
        r.put("%", "5,5");
        r.put("Capital restant dû", "1 000");
        r.put("% (2)", "12,25");
        r.put("Montant des dépôts nantis ou en garantie", "300");
        r.put("% du capital couvert", "30");

        SheetTable sheet = new SheetTable("M.IV.IMPAYE", null, HEADERS, List.of(r));

        List<SigImfSituationImpayeCredit> items = mapper.map(sheet);
        assertEquals(1, items.size());
        SigImfSituationImpayeCredit it = items.get(0);
        assertEquals("M.IV.IMP.2", it.getCode());
        assertEquals("IX", it.getElement());
        assertEquals(10.0, it.getNombresPrets(), 1e-9);
        assertEquals(5.5, it.getNombrePretPercent(), 1e-9);        // 1er "%"
        assertEquals(1000.0, it.getCapitalRestant(), 1e-9);
        assertEquals(12.25, it.getCapitalRestantPercent(), 1e-9);  // "% (2)" bien distinct
        assertEquals(300.0, it.getMontantDepotNantis(), 1e-9);
        assertEquals(30.0, it.getPercentCapitalCouvert(), 1e-9);
    }
}
