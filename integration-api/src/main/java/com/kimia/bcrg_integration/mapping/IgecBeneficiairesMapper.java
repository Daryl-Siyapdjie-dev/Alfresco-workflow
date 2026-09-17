package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.DixBeneficiares;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_09} (dix bénéficiaires par signature) → items
 * {@link DixBeneficiares} (endpoint {@code /api/imf/igec/igec09}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecBeneficiairesMapper implements SheetMapper<DixBeneficiares> {

    static final String CODE = "N°CODE";
    static final String DENOMINATION = "Dénomination complète";
    static final String TYPE = "Type Engagement";
    static final String DATE_OPERATION = "Date opération";
    static final String DATE_ECHEANCE = "Date échéance";
    static final String MONTANT = "Montant";
    static final String RETARD = "Retard";
    static final String PROVISION = "Provision comptabilisée";

    @Override
    public List<DixBeneficiares> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DENOMINATION, TYPE, MONTANT);
        List<DixBeneficiares> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new DixBeneficiares()
                    .code(code)
                    .denominationBeneficiaires(cols.get(row, DENOMINATION))
                    .typeEngagement(cols.get(row, TYPE))
                    .dateOperation(Dates.parseDate(cols.get(row, DATE_OPERATION), DATE_OPERATION))
                    .dateEcheance(Dates.parseDate(cols.get(row, DATE_ECHEANCE), DATE_ECHEANCE))
                    .montantEngagement(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT))
                    .retardEngagement(Numbers.parseMontantOuRenvoi(cols.get(row, RETARD), RETARD))
                    .provisionComptabilisee(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISION), PROVISION)));
        }
        return items;
    }
}
