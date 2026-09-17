package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfMxrsl;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide le mapping M.X.RSL → {@link SigImfMxrsl} (taux de pondération conservé en texte). */
class SigImfMxrslMapperTest {

    private final SigImfMxrslMapper mapper = new SigImfMxrslMapper();

    private static final List<String> HEADERS = List.of(
            "CODE", "ELEMENT", "N° Compte", "Autres engagements reçus",
            "MONTANT GNF", "TAUX DE PONDERATION", "MONTANT RETENU");

    @Test
    void mappe_les_sept_colonnes() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("CODE", "M.X.RSL.1");
        r.put("ELEMENT", "A");
        r.put("N° Compte", "50");
        r.put("Autres engagements reçus", "Engagements de crédit-bail mobilier");
        r.put("MONTANT GNF", "1 000");
        r.put("TAUX DE PONDERATION", "100");
        r.put("MONTANT RETENU", "1 000");
        SheetTable sheet = new SheetTable("M.X.RSL", null, HEADERS, List.of(r));

        List<SigImfMxrsl> items = mapper.map(sheet);
        assertEquals(1, items.size());
        SigImfMxrsl it = items.get(0);
        assertEquals("M.X.RSL.1", it.getCode());
        assertEquals("A", it.getElement());
        assertEquals(50.0, it.getnCompte(), 1e-9);
        assertEquals("Engagements de crédit-bail mobilier", it.getLibelle());
        assertEquals(1000.0, it.getMontantGnf(), 1e-9);
        assertEquals("100", it.getTauxPonderation());   // texte, non converti
        assertEquals(1000.0, it.getMontantRetenu(), 1e-9);
    }
}
