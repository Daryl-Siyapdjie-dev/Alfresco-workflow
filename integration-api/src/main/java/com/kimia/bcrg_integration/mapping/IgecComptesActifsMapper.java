package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SituationComptesActifsInactifs;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_14} (mouvements des comptes, par ancienneté) → items
 * {@link SituationComptesActifsInactifs} (endpoint {@code /api/imf/igec/igec14}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>Même remarque qu'{@code IGEC_13} sur la colonne des codes, qui numérote au lieu de coder.
 */
@Component
public class IgecComptesActifsMapper implements SheetMapper<SituationComptesActifsInactifs> {

    static final String CODE = "N°CODE";
    static final String INDICATEURS = "INDICATEURS";
    static final String ZERO_TROIS = "0-3 mois";
    static final String TROIS = ">= 3 mois";
    static final String SIX = "> = 6mois";
    static final String NEUF = "> = 9 mois";
    static final String DOUZE = ">= 12mois";
    static final String TOTAL = "Total";

    @Override
    public List<SituationComptesActifsInactifs> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), INDICATEURS, ZERO_TROIS, TOTAL);
        List<SituationComptesActifsInactifs> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new SituationComptesActifsInactifs()
                    .code(code)
                    .indicateurs(cols.get(row, INDICATEURS))
                    .zeroTroisMois(Numbers.parseMontantOuRenvoi(cols.get(row, ZERO_TROIS), ZERO_TROIS))
                    .plusOuEgalTroisMois(Numbers.parseMontantOuRenvoi(cols.get(row, TROIS), TROIS))
                    .plusOuEgalSixMois(Numbers.parseMontantOuRenvoi(cols.get(row, SIX), SIX))
                    .plusOuEgalNeufMois(Numbers.parseMontantOuRenvoi(cols.get(row, NEUF), NEUF))
                    .plusOuEgalDouzeMois(Numbers.parseMontantOuRenvoi(cols.get(row, DOUZE), DOUZE))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
