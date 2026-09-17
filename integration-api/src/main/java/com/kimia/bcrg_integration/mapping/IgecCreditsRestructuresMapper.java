package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CreditsRestructures;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_11} (crédits restructurés ou rééchelonnés) → items
 * {@link CreditsRestructures} (endpoint {@code /api/imf/igec/igec11}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecCreditsRestructuresMapper implements SheetMapper<CreditsRestructures> {

    static final String CODE = "N°CODE";
    static final String DENOMINATION = "Dénomination complète";
    static final String NATURE = "Nature de crédit";
    static final String DATE_MISE_EN_PLACE = "Date de mise en place";
    static final String DATE_REECHELONNEMENT = "Date rééchélonnement";
    static final String DATE_ECHEANCE = "Date échéance";
    static final String INITIALE = "Initiale";
    static final String RESTRUCTURE = "Restructuré";
    static final String RETARD = "Retard";
    static final String PROVISION = "Provision comptabilisée";

    @Override
    public List<CreditsRestructures> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DENOMINATION, NATURE, INITIALE, RESTRUCTURE);
        List<CreditsRestructures> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CreditsRestructures()
                    .code(code)
                    .denominationComplete(cols.get(row, DENOMINATION))
                    .natureCredit(cols.get(row, NATURE))
                    .dateMiseEnPlace(Dates.parseDate(cols.get(row, DATE_MISE_EN_PLACE), DATE_MISE_EN_PLACE))
                    .dateReechelonnement(Dates.parseDate(cols.get(row, DATE_REECHELONNEMENT), DATE_REECHELONNEMENT))
                    .dateEcheance(Dates.parseDate(cols.get(row, DATE_ECHEANCE), DATE_ECHEANCE))
                    .montantInitial(Numbers.parseMontantOuRenvoi(cols.get(row, INITIALE), INITIALE))
                    .montantRestructure(Numbers.parseMontantOuRenvoi(cols.get(row, RESTRUCTURE), RESTRUCTURE))
                    .montantRetard(Numbers.parseMontantOuRenvoi(cols.get(row, RETARD), RETARD))
                    .montantProvision(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISION), PROVISION)));
        }
        return items;
    }
}
