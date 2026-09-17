package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfDebiteurImportant;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide le mapping M.VIII.DIX.DEB → {@link SigImfDebiteurImportant} (liste « N°. »). */
class SigImfDebiteurImportantMapperTest {

    private final SigImfDebiteurImportantMapper mapper = new SigImfDebiteurImportantMapper();

    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et prénoms", "Date originale", "Montant original", "Solde",
            "Retard en capital", "Provision comptabilisée", "Garantie", "ENCOURS CREDIT GF");

    @Test
    void mappe_une_ligne_et_ignore_les_placeholders() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("N°.", "1");
        r.put("Nom et prénoms", "SARL Alpha");
        r.put("Date originale", "15/06/2019");
        r.put("Montant original", "50 000");
        r.put("Solde", "40 000");
        r.put("Retard en capital", "0");
        r.put("Provision comptabilisée", "0");
        r.put("Garantie", "Nantissement");
        r.put("ENCOURS CREDIT GF", "40 000");

        Map<String, String> vide = new LinkedHashMap<>(r);
        vide.put("Nom et prénoms", "");   // placeholder

        List<SigImfDebiteurImportant> items = mapper.map(new SheetTable("M.VIII.DIX.DEB", null, HEADERS, List.of(r, vide)));

        assertEquals(1, items.size());
        SigImfDebiteurImportant it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("SARL Alpha", it.getNomEtPrenoms());
        assertEquals(OffsetDateTime.of(2019, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateOriginale());
        assertEquals(50000.0, it.getMontantOriginal(), 1e-9);
        assertEquals("Nantissement", it.getGarantie());
    }
}
