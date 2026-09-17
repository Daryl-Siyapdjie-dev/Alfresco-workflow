package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCompteResultat;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Valide le mapping M.II.RESULTAT → {@link SigImfCompteResultat} sur des lignes propres. */
class SigImfCompteResultatMapperTest {

    private final SigImfCompteResultatMapper mapper = new SigImfCompteResultatMapper();

    private static final List<String> HEADERS = List.of("CODE", "N° compte", "CHARGES", "Montant");

    private Map<String, String> row(String code, String nCompte, String charges, String montant) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("N° compte", nCompte);
        m.put("CHARGES", charges);
        m.put("Montant", montant);
        return m;
    }

    @Test
    void mappe_les_lignes() {
        SheetTable sheet = new SheetTable("M.II.RESULTAT", null, HEADERS, List.of(
                row("M.II.R.C.601", "601", "Intérêts", "1 500,25"),
                row("", "", "Sous-total", "0")   // ligne sans code ignorée
        ));

        List<SigImfCompteResultat> items = mapper.map(sheet);

        assertEquals(1, items.size());
        SigImfCompteResultat r = items.get(0);
        assertEquals("M.II.R.C.601", r.getCode());
        assertEquals("601", r.getnCompte());
        assertEquals("Intérêts", r.getLibelle());
        assertEquals(1500.25, r.getMontant(), 1e-9);
    }

    @Test
    void rejette_un_montant_non_numerique() {
        SheetTable sheet = new SheetTable("M.II.RESULTAT", null, HEADERS, List.of(
                row("M.II.R.C.6", "6", "Produits", "=D5+D6")
        ));
        MappingException ex = assertThrows(MappingException.class, () -> mapper.map(sheet));
        assertTrue(ex.getMessage().contains("Montant"), ex.getMessage());
    }
}
