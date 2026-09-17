package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Fabrique l'en-tête {@link TransmissionImf} qui accompagne chaque feuille dans son
 * {@code DataModel…String}. Centralise la <strong>règle métier</strong> :
 * {@code dateArrete = dernier jour du mois} (constat validé côté BCRG), en minuit UTC.
 * <p>Le {@code statut} vaut {@code CREATION} pour un premier envoi, {@code MODIFICATION} pour une
 * correction, etc. (cf. enum {@link StatutEnum}).
 */
@Component
public class TransmissionFactory {

    /** En-tête pour une période mensuelle : {@code dateArrete} = dernier jour du mois. */
    public TransmissionImf forMonth(int year, int month, StatutEnum statut) {
        LocalDate dernierJour = YearMonth.of(year, month).atEndOfMonth();
        return forDateArrete(dernierJour, statut);
    }

    /** Raccourci : première transmission d'une période mensuelle ({@code statut = CREATION}). */
    public TransmissionImf creationForMonth(int year, int month) {
        return forMonth(year, month, StatutEnum.CREATION);
    }

    /** En-tête pour une {@code dateArrete} explicite (minuit UTC). */
    public TransmissionImf forDateArrete(LocalDate dateArrete, StatutEnum statut) {
        OffsetDateTime arrete = dateArrete.atStartOfDay().atOffset(ZoneOffset.UTC);
        return new TransmissionImf().dateArrete(arrete).statut(statut);
    }
}
