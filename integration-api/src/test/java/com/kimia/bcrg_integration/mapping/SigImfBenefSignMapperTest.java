package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBenefSign;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide le mapping M.XX.DIX.BENEF.SIGN → {@link SigImfBenefSign} (garantie numérique). */
class SigImfBenefSignMapperTest {

    private final SigImfBenefSignMapper mapper = new SigImfBenefSignMapper();

    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et prénoms", "Date originale", "Montant original", "Solde",
            "Retard en capital", "Provision comptabilisée", "Garantie");

    @Test
    void mappe_une_ligne_avec_garantie_numerique() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("N°.", "1");
        r.put("Nom et prénoms", "Mamadou Diallo");
        r.put("Date originale", "01/02/2019");
        r.put("Montant original", "20 000");
        r.put("Solde", "18 000");
        r.put("Retard en capital", "500");
        r.put("Provision comptabilisée", "100");
        r.put("Garantie", "15 000");

        List<SigImfBenefSign> items = mapper.map(new SheetTable("M.XX.DIX.BENEF.SIGN", null, HEADERS, List.of(r)));

        assertEquals(1, items.size());
        SigImfBenefSign it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("Mamadou Diallo", it.getNomEtPrenoms());
        assertEquals(OffsetDateTime.of(2019, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateOriginale());
        assertEquals(18000.0, it.getSolde(), 1e-9);
        assertEquals(500.0, it.getRetardCapital(), 1e-9);
        assertEquals(15000.0, it.getGarantie(), 1e-9);   // garantie = nombre
    }
}
