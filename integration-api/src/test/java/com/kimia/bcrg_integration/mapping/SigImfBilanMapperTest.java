package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBilan;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide le mapping M.I.BILAN → {@link SigImfBilan} sur des lignes propres (représentatives d'un
 * fichier source rempli). On n'utilise pas le template SIGIMF ici : ses colonnes de montant
 * contiennent des formules Excel, pas des valeurs (cf. Javadoc du mapper).
 */
class SigImfBilanMapperTest {

    private final SigImfBilanMapper mapper = new SigImfBilanMapper();

    private static final List<String> HEADERS = List.of(
            "CODE", "N° compte", "ACTIF", "Montant brut", "Amortissement & Provisions", "Montant net");

    private Map<String, String> row(String code, String nCompte, String libelle,
                                    String brut, String amort, String net) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CODE", code);
        m.put("N° compte", nCompte);
        m.put("ACTIF", libelle);
        m.put("Montant brut", brut);
        m.put("Amortissement & Provisions", amort);
        m.put("Montant net", net);
        return m;
    }

    @Test
    void mappe_des_lignes_numeriques_propres() {
        SheetTable sheet = new SheetTable("M.I.BILAN", null, HEADERS, List.of(
                row("M.I.B.A.101", "101", "Caisses", "1 234,56", "34,56", "1 200,00"),
                row("M.I.B.A.102", "102", "Comptes ordinaires", "2 000", "", "2 000")
        ));

        List<SigImfBilan> items = mapper.map(sheet);

        assertEquals(2, items.size());

        SigImfBilan first = items.get(0);
        assertEquals("M.I.B.A.101", first.getCode());
        assertEquals("101", first.getnCompte());
        assertEquals("Caisses", first.getLibelle());
        assertEquals(1234.56, first.getMontantBrut(), 1e-9);
        assertEquals(34.56, first.getAmortissementProvision(), 1e-9);
        assertEquals(1200.00, first.getMontantNet(), 1e-9);

        // colonne vide → null (et non 0)
        assertNull(items.get(1).getAmortissementProvision());
        assertEquals(2000.0, items.get(1).getMontantBrut(), 1e-9);
    }

    @Test
    void ignore_les_lignes_sans_code() {
        SheetTable sheet = new SheetTable("M.I.BILAN", null, HEADERS, List.of(
                row("", "", "Sous-total", "", "", ""),
                row("M.I.B.A.101", "101", "Caisses", "10", "", "10")
        ));
        assertEquals(1, mapper.map(sheet).size());
    }

    @Test
    void rejette_une_valeur_de_montant_non_numerique() {
        SheetTable sheet = new SheetTable("M.I.BILAN", null, HEADERS, List.of(
                row("M.I.B.A.1", "1", "Operations", "D7-E7", "", "D8-E8")   // formule = donnée invalide
        ));
        MappingException ex = assertThrows(MappingException.class, () -> mapper.map(sheet));
        assertTrue(ex.getMessage().contains("Montant brut"), ex.getMessage());
    }

    @Test
    void signale_une_colonne_obligatoire_absente() {
        SheetTable sheet = new SheetTable("M.I.BILAN", null, List.of("CODE", "ACTIF"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
