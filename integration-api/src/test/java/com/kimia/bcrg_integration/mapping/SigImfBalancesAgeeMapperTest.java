package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBalancesAgee;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valide le mapping M.XXI.BALANCES AGEES → {@link SigImfBalancesAgee}. La feuille enchaîne quatre
 * blocs de même structure mais d'intitulés différents : le lecteur ne conserve que les en-têtes du
 * 1er bloc, les lignes des blocs suivants étant alignées <strong>par position</strong>. Le test
 * reproduit cette situation (une ligne du bloc B lue sous les en-têtes du bloc A).
 */
class SigImfBalancesAgeeMapperTest {

    private final SigImfBalancesAgeeMapper mapper = new SigImfBalancesAgeeMapper();

    private static final List<String> HEADERS = List.of("CODE", "ECHEANCES", "Nombre de dettes", "%",
            "Montant originel de la dette", "Capital restant dû", "% (2)");

    private Map<String, String> row(String... values) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < HEADERS.size(); i++) m.put(HEADERS.get(i), i < values.length ? values[i] : "");
        return m;
    }

    @Test
    void mappe_les_lignes_des_differents_blocs_par_position() {
        SheetTable sheet = new SheetTable("M.XXI.BALANCES AGEES", null, HEADERS, List.of(
                row("M.XXI.BAL.1", "1 Mois", "3", "0,25", "1 000 000", "800 000", "0,8"),
                row("", "B)-PORTEFEUILLE DE CREDITS SAINS"),                       // titre de bloc
                row("M.XXI.BAL.10", "1 Mois", "4", "0,1", "2 000 000", "1 500 000", "0,75")
        ));

        List<SigImfBalancesAgee> items = mapper.map(sheet);

        assertEquals(2, items.size(), "les titres de bloc ne sont pas des données");
        SigImfBalancesAgee a = items.get(0);
        assertEquals("M.XXI.BAL.1", a.getCode());
        assertEquals("1 Mois", a.getEcheances());
        assertEquals(3.0, a.getNombresDettes(), 1e-9);
        assertEquals(0.25, a.getNombresPercent(), 1e-9);
        assertEquals(1_000_000.0, a.getMontantOriginel(), 1e-9);
        assertEquals(800_000.0, a.getCapitalRestant(), 1e-9);
        assertEquals(0.8, a.getCapitalPercent(), 1e-9);

        // ligne du bloc B : mêmes positions, intitulés du bloc A
        SigImfBalancesAgee b = items.get(1);
        assertEquals("M.XXI.BAL.10", b.getCode());
        assertEquals(4.0, b.getNombresDettes(), 1e-9);
        assertEquals(1_500_000.0, b.getCapitalRestant(), 1e-9);
    }

    @Test
    void refuse_une_feuille_a_moins_de_sept_colonnes() {
        SheetTable sheet = new SheetTable("M.XXI.BALANCES AGEES", null,
                List.of("CODE", "ECHEANCES", "Nombre de dettes"), List.of());
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
