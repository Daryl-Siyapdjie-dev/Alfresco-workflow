package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valide la règle {@code dateArrete = dernier jour du mois} du {@link TransmissionFactory}. */
class TransmissionFactoryTest {

    private final TransmissionFactory factory = new TransmissionFactory();

    @Test
    void dateArrete_est_le_dernier_jour_du_mois() {
        TransmissionImf t = factory.forMonth(2019, 3, StatutEnum.CREATION);
        assertEquals(OffsetDateTime.of(2019, 3, 31, 0, 0, 0, 0, ZoneOffset.UTC), t.getDateArrete());
        assertEquals(StatutEnum.CREATION, t.getStatut());
    }

    @Test
    void gere_fevrier_bissextile_et_non_bissextile() {
        assertEquals(OffsetDateTime.of(2020, 2, 29, 0, 0, 0, 0, ZoneOffset.UTC),
                factory.forMonth(2020, 2, StatutEnum.CREATION).getDateArrete());   // bissextile
        assertEquals(OffsetDateTime.of(2019, 2, 28, 0, 0, 0, 0, ZoneOffset.UTC),
                factory.forMonth(2019, 2, StatutEnum.CREATION).getDateArrete());   // non bissextile
    }

    @Test
    void creationForMonth_met_le_statut_CREATION() {
        assertEquals(StatutEnum.CREATION, factory.creationForMonth(2026, 8).getStatut());
    }
}
