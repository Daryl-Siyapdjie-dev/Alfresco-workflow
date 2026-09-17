package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRapportCollecte;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Valide le mapper mutualisé {@link SigImfRapportCollecteMapper} (M.XIII.OATA / M.XIV.FS / M.XVI.CERS),
 * y compris le cas où la colonne optionnelle « N° Compte » est absente (feuille FS).
 */
class SigImfRapportCollecteMapperTest {

    private final SigImfRapportCollecteMapper mapper = new SigImfRapportCollecteMapper();

    @Test
    void mappe_avec_colonne_ncompte_presente() {   // cas OATA / CERS
        List<String> headers = List.of("CODE", "ELEMENT", "N° Compte", "LIBELLE", "MONTANT GNF");
        Map<String, String> r = new LinkedHashMap<>();
        r.put("CODE", "M.XIII.OATA.C.5");
        r.put("ELEMENT", "I");
        r.put("N° Compte", "715");
        r.put("LIBELLE", "Dépôt à vue");
        r.put("MONTANT GNF", "12 500,00");
        SheetTable sheet = new SheetTable("M.XIII.OATA", null, headers, List.of(r));

        List<SigImfRapportCollecte> items = mapper.map(sheet);
        assertEquals(1, items.size());
        SigImfRapportCollecte it = items.get(0);
        assertEquals("M.XIII.OATA.C.5", it.getCode());
        assertEquals("I", it.getElement());
        assertEquals(715.0, it.getnCompte(), 1e-9);
        assertEquals("Dépôt à vue", it.getLibelle());
        assertEquals(12500.00, it.getMontantGnf(), 1e-9);
    }

    @Test
    void mappe_sans_colonne_ncompte() {   // cas FS : nCompte absent → null
        List<String> headers = List.of("CODE", "ELEMENT", "LIBELLE", "MONTANT GNF");
        Map<String, String> r = new LinkedHashMap<>();
        r.put("CODE", "M.XIV.FS.B.4");
        r.put("ELEMENT", "I");
        r.put("LIBELLE", "Réserve légale obligatoire");
        r.put("MONTANT GNF", "3 000");
        SheetTable sheet = new SheetTable("M.XIV.FS", null, headers, List.of(r));

        List<SigImfRapportCollecte> items = mapper.map(sheet);
        assertEquals(1, items.size());
        assertNull(items.get(0).getnCompte());
        assertEquals(3000.0, items.get(0).getMontantGnf(), 1e-9);
    }
}
