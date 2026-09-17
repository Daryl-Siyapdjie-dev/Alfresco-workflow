package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfHorsBilan;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Valide le mapping M.III.HS_BILAN → {@link SigImfHorsBilan} sur des lignes propres. */
class SigImfHorsBilanMapperTest {

    private final SigImfHorsBilanMapper mapper = new SigImfHorsBilanMapper();

    private static final List<String> HEADERS = List.of("CODE", "N° compte", "LIBELLES", "Montant");

    private Map<String, String> row(String code, String nCompte, String libelle, String montant) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("N° compte", nCompte);
        m.put("LIBELLES", libelle);
        m.put("Montant", montant);
        return m;
    }

    @Test
    void mappe_les_lignes_et_gere_le_montant_vide() {
        SheetTable sheet = new SheetTable("M.III.HS_BILAN", null, HEADERS, List.of(
                row("M.III.HB.901", "90", "Reprise sur autres provisions", "42 000"),
                row("M.III.HB.9011", "901", "Subventions d'exploitation", "")   // montant vide → null
        ));

        List<SigImfHorsBilan> items = mapper.map(sheet);

        assertEquals(2, items.size());
        assertEquals("M.III.HB.901", items.get(0).getCode());
        assertEquals("90", items.get(0).getnCompte());
        assertEquals("Reprise sur autres provisions", items.get(0).getLibelle());
        assertEquals(42000.0, items.get(0).getMontant(), 1e-9);
        assertNull(items.get(1).getMontant());
    }

    @Test
    void rejette_un_montant_non_numerique() {
        SheetTable sheet = new SheetTable("M.III.HS_BILAN", null, HEADERS, List.of(
                row("M.III.HB.902", "902", "Recouvrement", "N/A")
        ));
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
