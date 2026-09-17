package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ProvisionCreditsSouffrance;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>second</strong> tableau de {@code FINS_12} — les <em>provisions requises par la
 * BCRG</em> sur ces mêmes encours → items {@link ProvisionCreditsSouffrance}, déposés dans
 * {@code items2}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le second. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des encours bruts.
 * <p>Mêmes tranches que le premier tableau, mais le template ne les écrit pas tout à fait pareil
 * (« 90-179 jours » ici, « 90-179 » là-haut) : d'où deux mappers plutôt qu'un mapper paramétré.
 */
@Component
public class FinsProvisionsSouffranceMapper implements SheetMapper<ProvisionCreditsSouffrance> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "PROVISIONS REQUISES PAR LA BCRG";
    static final String MOINS_30 = "< 30 jours";
    static final String T30_89 = "30-89 jours";
    static final String T90_179 = "90-179 jours";
    static final String T180_359 = "180-359 jours";
    static final String T360_719 = "360-719 jours";
    static final String PLUS_720 = ">=720 jours";
    static final String TOTAL = "TOTAL";

    @Override
    public List<ProvisionCreditsSouffrance> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, MOINS_30, TOTAL);
        List<ProvisionCreditsSouffrance> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ProvisionCreditsSouffrance()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .inferieurTrenteJours(Numbers.parseMontantOuRenvoi(cols.get(row, MOINS_30), MOINS_30))
                    .intervalleTrenteEtQuatreVingtneufJours(Numbers.parseMontantOuRenvoi(cols.get(row, T30_89), T30_89))
                    .intervalleQuatreVingtDixEtCentSoixanteDixNeufJours(Numbers.parseMontantOuRenvoi(cols.get(row, T90_179), T90_179))
                    .intervalleCentQuatreVingtEtTroisCentCinquanteNeufJours(Numbers.parseMontantOuRenvoi(cols.get(row, T180_359), T180_359))
                    .intervalleTroisCentSoixanteEtSeptCentDixNeufJours(Numbers.parseMontantOuRenvoi(cols.get(row, T360_719), T360_719))
                    .superieurOuEgalSeptCentVingtJours(Numbers.parseMontantOuRenvoi(cols.get(row, PLUS_720), PLUS_720))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
