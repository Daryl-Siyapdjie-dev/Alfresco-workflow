package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.EncourSouffranceBrutImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>premier</strong> tableau de {@code FINS_12} — les <em>encours en souffrance
 * bruts</em>, par tranche d'ancienneté → items {@link EncourSouffranceBrutImf}
 * (endpoint {@code /api/imf/fins/fins12}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le premier. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des encours bruts.
 */
@Component
public class FinsAncienneteSouffranceMapper implements SheetMapper<EncourSouffranceBrutImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "Expositions en souffrance brutes";
    static final String MOINS_30 = "<30 jours";
    static final String T30_89 = "30-89 jours";
    static final String T90_179 = "90-179";
    static final String T180_359 = "180-359";
    static final String T360_719 = "360-719";
    static final String PLUS_720 = ">=720 jours";
    static final String TOTAL = "Total";

    @Override
    public List<EncourSouffranceBrutImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, MOINS_30, TOTAL);
        List<EncourSouffranceBrutImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code, sheet.sheetName())) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new EncourSouffranceBrutImf()
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
