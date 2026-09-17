package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ResiduelleRessourceImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>premier</strong> tableau de {@code FINS_16} — la <em>durée résiduelle des
 * ressources</em> en capital restant dû → items {@link ResiduelleRessourceImf}
 * (endpoint {@code /api/imf/fins/fins16}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le premier. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des montants.
 */
@Component
public class FinsDureeResiduelleMapper implements SheetMapper<ResiduelleRessourceImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "Capital restant dû";
    static final String UN_SIX = "1-6 Mois";
    static final String SEPT_DOUZE = "7-12 Mois";
    static final String TREIZE_SOIXANTE = "13-60 Mois";
    static final String PLUS_61 = "> 61 Mois";
    static final String TOTAL = "TOTAL";

    @Override
    public List<ResiduelleRessourceImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, UN_SIX, TOTAL);
        List<ResiduelleRessourceImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code, sheet.sheetName())) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ResiduelleRessourceImf()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .intervalleUnEtSix(Numbers.parseMontantOuRenvoi(cols.get(row, UN_SIX), UN_SIX))
                    .intervalleSeptEtDouze(Numbers.parseMontantOuRenvoi(cols.get(row, SEPT_DOUZE), SEPT_DOUZE))
                    .intervalleTreizeEtSoixante(Numbers.parseMontantOuRenvoi(cols.get(row, TREIZE_SOIXANTE), TREIZE_SOIXANTE))
                    .intervalleSuperieurCentSoixanteEtUn(Numbers.parseMontantOuRenvoi(cols.get(row, PLUS_61), PLUS_61))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
