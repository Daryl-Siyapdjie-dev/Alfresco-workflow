package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfTauxProvision;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Valide le mapping M.XVII.PROV → {@link SigImfTauxProvision} sur des lignes propres. */
class SigImfTauxProvisionMapperTest {

    private final SigImfTauxProvisionMapper mapper = new SigImfTauxProvisionMapper();

    private static final List<String> HEADERS =
            List.of("CODE", "ELEMENT", "N° Compte", "LIBELLE", "Montant en souffrance", "Norme", "Provisions");

    private Map<String, String> row(String code, String element, String nCompte, String libelle,
                                    String souffrance, String norme, String provisions) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("ELEMENT", element);
        m.put("N° Compte", nCompte);
        m.put("LIBELLE", libelle);
        m.put("Montant en souffrance", souffrance);
        m.put("Norme", norme);
        m.put("Provisions", provisions);
        return m;
    }

    @Test
    void mappe_les_lignes_de_provision() {
        SheetTable sheet = new SheetTable("M.XVII.PROV", null, HEADERS, List.of(
                row("M.XX.PROV.3", "", "112", "Retards de 30 à 89 jours", "2 000 000", "0,25", "500 000"),
                row("", "", "", "ligne sans code", "1", "1", "1"),
                row("M.XX.PROV.10", "II", "", "TOTAL PROVISIONS CONSTITUEES", "", "", "750 000")
        ));

        List<SigImfTauxProvision> items = mapper.map(sheet);

        assertEquals(2, items.size());
        SigImfTauxProvision p = items.get(0);
        assertEquals("M.XX.PROV.3", p.getCode());
        assertEquals(112.0, p.getnCompte(), 1e-9);
        assertEquals("Retards de 30 à 89 jours", p.getLibelle());
        assertEquals(2_000_000.0, p.getMontantSouffrance(), 1e-9);
        assertEquals(0.25, p.getNorme(), 1e-9);
        assertEquals(500_000.0, p.getProvisions(), 1e-9);

        SigImfTauxProvision total = items.get(1);
        assertEquals("II", total.getElement());
        assertNull(total.getMontantSouffrance(), "colonne vide → null");
        assertEquals(750_000.0, total.getProvisions(), 1e-9);
    }

    @Test
    void rejette_une_provision_non_numerique() {
        SheetTable sheet = new SheetTable("M.XVII.PROV", null, HEADERS, List.of(
                row("M.XX.PROV.3", "", "112", "Retards", "2 000", "0,25", "n/d")
        ));
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }

    @Test
    void exige_les_colonnes_obligatoires() {
        SheetTable sheet = new SheetTable("M.XVII.PROV", null, List.of("CODE", "ELEMENT"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
