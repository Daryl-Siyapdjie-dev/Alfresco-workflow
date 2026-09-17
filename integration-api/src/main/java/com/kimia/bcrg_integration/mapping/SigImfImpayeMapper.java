package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSituationImpayeCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.IV.IMPAYE} → items {@link SigImfSituationImpayeCredit}
 * (endpoint {@code /api/sigimf/m4impaye}, DataModel {@code DataModelImfSigImfSituationImpayeCreditString}).
 * <p>La feuille a <strong>deux colonnes d'en-tête « % » homonymes</strong> ; le lecteur les
 * désambiguïse en {@code "%"} (taux du nombre de prêts) et {@code "% (2)"} (taux du capital restant).
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code Nombres de prêts→nombresPrets},
 * {@code %→nombrePretPercent}, {@code Capital restant dû→capitalRestant},
 * {@code % (2)→capitalRestantPercent}, {@code Montant des dépôts nantis ou en garantie→montantDepotNantis},
 * {@code % du capital couvert→percentCapitalCouvert}. Champs {@code norme}/{@code section} sans colonne
 * source directe → non renseignés (à cadrer dans la matrice).
 */
@Component
public class SigImfImpayeMapper implements SheetMapper<SigImfSituationImpayeCredit> {

    private static final String COL_CODE = "CODE";
    private static final String COL_ELEMENT = "ELEMENT";
    private static final String COL_NB_PRETS = "Nombres de prêts";
    private static final String COL_NB_PRETS_PCT = "%";
    private static final String COL_CAPITAL = "Capital restant dû";
    private static final String COL_CAPITAL_PCT = "% (2)";
    private static final String COL_DEPOT_NANTIS = "Montant des dépôts nantis ou en garantie";
    private static final String COL_CAPITAL_COUVERT_PCT = "% du capital couvert";

    @Override
    public List<SigImfSituationImpayeCredit> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                COL_CODE, COL_ELEMENT, COL_NB_PRETS, COL_NB_PRETS_PCT, COL_CAPITAL,
                COL_CAPITAL_PCT, COL_DEPOT_NANTIS, COL_CAPITAL_COUVERT_PCT);
        List<SigImfSituationImpayeCredit> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, COL_CODE);
            if (code.isEmpty()) continue;
            items.add(new SigImfSituationImpayeCredit()
                    .code(code)
                    .element(cols.get(row, COL_ELEMENT))
                    .nombresPrets(Numbers.parseFrenchDouble(cols.get(row, COL_NB_PRETS), COL_NB_PRETS))
                    .nombrePretPercent(Numbers.parseFrenchDouble(cols.get(row, COL_NB_PRETS_PCT), "%"))
                    .capitalRestant(Numbers.parseFrenchDouble(cols.get(row, COL_CAPITAL), COL_CAPITAL))
                    .capitalRestantPercent(Numbers.parseFrenchDouble(cols.get(row, COL_CAPITAL_PCT), "% (capital)"))
                    .montantDepotNantis(Numbers.parseFrenchDouble(cols.get(row, COL_DEPOT_NANTIS), COL_DEPOT_NANTIS))
                    .percentCapitalCouvert(
                            Numbers.parseFrenchDouble(cols.get(row, COL_CAPITAL_COUVERT_PCT), COL_CAPITAL_COUVERT_PCT)));
        }
        return items;
    }
}
