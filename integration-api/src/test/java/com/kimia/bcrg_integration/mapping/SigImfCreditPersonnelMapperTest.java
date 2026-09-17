package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSCreditAPersonnel;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide le mapping M.VII.PERSONNEL → {@link SigImfSCreditAPersonnel} (liste, en-tête « N°. »). */
class SigImfCreditPersonnelMapperTest {

    private final SigImfCreditPersonnelMapper mapper = new SigImfCreditPersonnelMapper();

    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et Prénoms", "Date originale", "Montant original",
            "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");

    private Map<String, String> row(String num, String nom, String date, String montant,
                                    String solde, String retard, String prov, String garantie) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("N°.", num);
        m.put("Nom et Prénoms", nom);
        m.put("Date originale", date);
        m.put("Montant original", montant);
        m.put("Solde", solde);
        m.put("Retard en capital", retard);
        m.put("Provision comptabilisée", prov);
        m.put("Garantie", garantie);
        return m;
    }

    @Test
    void mappe_une_ligne_et_ignore_les_placeholders() {
        SheetTable sheet = new SheetTable("M.VII.PERSONNEL", null, HEADERS, List.of(
                row("1", "Awa Camara", "03/01/2020", "5 000,75", "4 000", "250", "100", "Salaire domicilié"),
                row("2", "", "", "", "", "", "", "")
        ));

        List<SigImfSCreditAPersonnel> items = mapper.map(sheet);

        assertEquals(1, items.size());
        SigImfSCreditAPersonnel it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("Awa Camara", it.getNomEtPrenoms());
        assertEquals(OffsetDateTime.of(2020, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateOriginale());
        assertEquals(5000.75, it.getMontantOriginal(), 1e-9);
        assertEquals(4000.0, it.getSolde(), 1e-9);
        assertEquals("Salaire domicilié", it.getGarantie());
    }
}
