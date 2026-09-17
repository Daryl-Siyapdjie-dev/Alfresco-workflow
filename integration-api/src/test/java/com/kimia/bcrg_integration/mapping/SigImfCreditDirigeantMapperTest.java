package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSCreditADirigeant;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Valide le mapping M.VI.ADM.DIR → {@link SigImfSCreditADirigeant} (liste, en-tête « N°. »). */
class SigImfCreditDirigeantMapperTest {

    private final SigImfCreditDirigeantMapper mapper = new SigImfCreditDirigeantMapper();

    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et prénoms", "Fonction", "Date d'octroi", "Montant initial",
            "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");

    private Map<String, String> row(String num, String nom, String fonction, String date,
                                    String montant, String solde, String retard, String prov, String garantie) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("N°.", num);
        m.put("Nom et prénoms", nom);
        m.put("Fonction", fonction);
        m.put("Date d'octroi", date);
        m.put("Montant initial", montant);
        m.put("Solde", solde);
        m.put("Retard en capital", retard);
        m.put("Provision comptabilisée", prov);
        m.put("Garantie", garantie);
        return m;
    }

    @Test
    void mappe_une_ligne_et_ignore_les_placeholders() {
        SheetTable sheet = new SheetTable("M.VI.ADM.DIR", null, HEADERS, List.of(
                row("1", "Jean Dupont", "DG", "12/05/2018", "10 000", "8 000", "0", "0", "Hypothèque"),
                row("2", "", "", "", "", "", "", "", "")   // pas de bénéficiaire → ignorée
        ));

        List<SigImfSCreditADirigeant> items = mapper.map(sheet);

        assertEquals(1, items.size());
        SigImfSCreditADirigeant it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("Jean Dupont", it.getNomEtPrenoms());
        assertEquals("DG", it.getFonction());
        assertEquals(OffsetDateTime.of(2018, 5, 12, 0, 0, 0, 0, ZoneOffset.UTC), it.getDateOctroi());
        assertEquals(10000.0, it.getMontantInitial(), 1e-9);
        assertEquals(8000.0, it.getSolde(), 1e-9);
        assertEquals("Hypothèque", it.getGarantie());
    }

    @Test
    void rejette_une_date_invalide() {
        SheetTable sheet = new SheetTable("M.VI.ADM.DIR", null, HEADERS, List.of(
                row("1", "Jean Dupont", "DG", "2018-05-12", "10 000", "8 000", "0", "0", "-")
        ));
        assertThrows(MappingException.class, () -> mapper.map(sheet));
    }
}
