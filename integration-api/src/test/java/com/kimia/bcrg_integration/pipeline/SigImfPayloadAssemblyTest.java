package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfBilanString;
import com.kimia.bcrg_integration.client.dto.SigImfBilan;
import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import com.kimia.bcrg_integration.excel.SheetTable;
import com.kimia.bcrg_integration.mapping.SigImfBilanMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Démontre l'<strong>assemblage complet d'un payload</strong> pour une feuille : items produits par un
 * mapper + en-tête {@link TransmissionFactory} → {@code DataModelImfSigImfBilanString} prêt à POSTer.
 * <p>On part de données propres synthétiques (le template réel de M.I.BILAN contient des formules,
 * cf. mapper). Le même patron « {@code new DataModel….items(items).transmission(t)} » vaut pour
 * toutes les feuilles.
 */
class SigImfPayloadAssemblyTest {

    private final SigImfBilanMapper bilanMapper = new SigImfBilanMapper();
    private final TransmissionFactory transmissions = new TransmissionFactory();

    private SheetTable bilanPropre() {
        List<String> headers = List.of(
                "CODE", "N° compte", "ACTIF", "Montant brut", "Amortissement & Provisions", "Montant net");
        Map<String, String> r = new LinkedHashMap<>();
        r.put("CODE", "M.I.B.A.101");
        r.put("N° compte", "101");
        r.put("ACTIF", "Caisses");
        r.put("Montant brut", "1 000");
        r.put("Amortissement & Provisions", "");
        r.put("Montant net", "1 000");
        return new SheetTable("M.I.BILAN", null, headers, List.of(r));
    }

    @Test
    void assemble_items_plus_transmission_dans_le_datamodel() {
        List<SigImfBilan> items = bilanMapper.map(bilanPropre());

        DataModelImfSigImfBilanString payload = new DataModelImfSigImfBilanString()
                .items(items)
                .transmission(transmissions.creationForMonth(2019, 3));

        // items bien portés
        assertEquals(1, payload.getItems().size());
        assertSame(items.get(0), payload.getItems().get(0));
        assertEquals("M.I.B.A.101", payload.getItems().get(0).getCode());

        // en-tête de transmission : dateArrete = 31/03/2019, statut CREATION
        assertEquals(OffsetDateTime.of(2019, 3, 31, 0, 0, 0, 0, ZoneOffset.UTC),
                payload.getTransmission().getDateArrete());
        assertEquals(StatutEnum.CREATION, payload.getTransmission().getStatut());
    }
}
