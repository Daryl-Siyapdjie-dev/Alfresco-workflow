package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRepartitionPCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valide le mapping M.XI.RPCS → {@link SigImfRepartitionPCredit}. Les en-têtes reprennent
 * <strong>tels quels</strong> ceux du fichier réel, alignés à coups d'espaces multiples : le test
 * vérifie donc aussi que la résolution de colonnes les normalise.
 */
class SigImfRepartitionPCreditMapperTest {

    private final SigImfRepartitionPCreditMapper mapper = new SigImfRepartitionPCreditMapper();

    private static final String AGRI = "Agriculture Elevage       Pêche";
    private static final String TP = "Travaux publics       Bâtiments   Logements";
    private static final String COMMERCE = "Commerce  Restaurant  Hôtellerie";
    private static final String INDUSTRIE = "Industrie     Artisanat";
    private static final String TRANSPORT = "Transport    Communication";

    private static final List<String> HEADERS =
            List.of("CODE", "ELEMENT", AGRI, TP, COMMERCE, INDUSTRIE, TRANSPORT, "Autres", "TOTAL");

    private Map<String, String> row(String code, String element, String... montants) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("ELEMENT", element);
        String[] cols = {AGRI, TP, COMMERCE, INDUSTRIE, TRANSPORT, "Autres", "TOTAL"};
        for (int i = 0; i < cols.length; i++) m.put(cols[i], i < montants.length ? montants[i] : "");
        return m;
    }

    @Test
    void mappe_les_colonnes_sectorielles_malgre_les_espaces_des_entetes() {
        SheetTable sheet = new SheetTable("M.XI.RPCS", null, HEADERS, List.of(
                row("M.XI.RPCS.2", "Ménages", "1 000", "2 000", "3 000", "4 000", "5 000", "6 000", "21 000"),
                row("", "titre de sous-bloc"),
                row("M.XI.RPCS.11", "Basse Guinée", "", "", "", "", "", "", "0")
        ));

        List<SigImfRepartitionPCredit> items = mapper.map(sheet);

        assertEquals(2, items.size());
        SigImfRepartitionPCredit m = items.get(0);
        assertEquals("M.XI.RPCS.2", m.getCode());
        assertEquals("Ménages", m.getElement());
        assertEquals(1_000.0, m.getAgriElevage(), 1e-9);
        assertEquals(2_000.0, m.getTravauxPublics(), 1e-9);
        assertEquals(3_000.0, m.getCommerceRestaurant(), 1e-9);
        assertEquals(4_000.0, m.getIndustrieArtisanat(), 1e-9);
        assertEquals(5_000.0, m.getTransportCommunication(), 1e-9);
        assertEquals(6_000.0, m.getAutres(), 1e-9);
        assertEquals(21_000.0, m.getTotal(), 1e-9);

        assertNull(items.get(1).getAgriElevage(), "colonne vide → null");
        assertEquals(0.0, items.get(1).getTotal(), 1e-9);
    }

    @Test
    void exige_les_colonnes_sectorielles() {
        SheetTable sheet = new SheetTable("M.XI.RPCS", null, List.of("CODE", "ELEMENT", "TOTAL"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
