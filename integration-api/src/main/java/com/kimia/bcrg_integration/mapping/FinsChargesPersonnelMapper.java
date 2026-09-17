package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ChargePersonnelImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>premier</strong> tableau de {@code FINS_14} — la <em>répartition des charges
 * de personnel</em> par catégorie → items {@link ChargePersonnelImf}
 * (endpoint {@code /api/imf/fins/fins14}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le premier. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des charges.
 * <p>En-tête sur <strong>une</strong> ligne : la suivante ne fait que numéroter les colonnes et
 * remplacerait « Cadres Supérieurs » par « 1 ».
 * <p>{@code cadreSuperieur} et {@code total} sont des entiers 64 bits côté cible, les autres 32 :
 * c'est la cible qui l'impose, la source ne distingue pas. {@code valeurTotalLigne} n'a pas de source.
 */
@Component
public class FinsChargesPersonnelMapper implements SheetMapper<ChargePersonnelImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "LIBELLES";
    static final String SUPERIEURS = "Cadres Supérieurs";
    static final String MOYENS = "Cadres Moyens";
    static final String SUBALTERNES = "Cadres Subalternes";
    static final String CONTRACTUELS = "Contractuels et occasionnels";
    static final String TOTAL = "TOTAL";

    @Override
    public List<ChargePersonnelImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, SUPERIEURS, TOTAL);
        List<ChargePersonnelImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code, sheet.sheetName())) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ChargePersonnelImf()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .cadreSuperieur(Numbers.parseLongOuRenvoi(cols.get(row, SUPERIEURS), SUPERIEURS))
                    .cadreMoyen(Numbers.parseEntierOuRenvoi(cols.get(row, MOYENS), MOYENS))
                    .cadreSubalterne(Numbers.parseEntierOuRenvoi(cols.get(row, SUBALTERNES), SUBALTERNES))
                    .contratuelOccasionnel(Numbers.parseEntierOuRenvoi(cols.get(row, CONTRACTUELS), CONTRACTUELS))
                    .total(Numbers.parseLongOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
