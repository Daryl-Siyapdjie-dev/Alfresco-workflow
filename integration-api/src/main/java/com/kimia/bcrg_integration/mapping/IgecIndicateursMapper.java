package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.IndicateursActivites;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_13} (indicateurs d'activité par région) → items
 * {@link IndicateursActivites} (endpoint {@code /api/imf/igec/igec13}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>⚠️ La colonne des codes de cette feuille porte une <strong>numérotation</strong> (« 1 », « 2 »…)
 * et non des codes {@code RN0_…}. Elle est transmise telle quelle — c'est la colonne de code du
 * template, et SIRYF est seul juge de sa nomenclature — mais le point est à confirmer côté BCRG.
 */
@Component
public class IgecIndicateursMapper implements SheetMapper<IndicateursActivites> {

    static final String CODE = "N°CODE";
    static final String INDICATEURS = "INDICATEURS";
    static final String CONAKRY = "CONAKRY";
    static final String BASSE_GUINEE = "BASSE GUINEE";
    static final String MOYENNE_GUINEE = "MOYENNE GUINEE";
    static final String HAUTE_GUINEE = "HAUTE GUINEE";
    static final String GUINEE_FORESTIERE = "GUINEE FORESTIERE";
    static final String TOTAL = "Total";

    @Override
    public List<IndicateursActivites> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), INDICATEURS, CONAKRY, TOTAL);
        List<IndicateursActivites> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new IndicateursActivites()
                    .code(code)
                    .indicateurs(cols.get(row, INDICATEURS))
                    .conakry(Numbers.parseEntierOuRenvoi(cols.get(row, CONAKRY), CONAKRY))
                    .basseGuinee(Numbers.parseEntierOuRenvoi(cols.get(row, BASSE_GUINEE), BASSE_GUINEE))
                    .moyenneGuinee(Numbers.parseEntierOuRenvoi(cols.get(row, MOYENNE_GUINEE), MOYENNE_GUINEE))
                    .hauteGuinee(Numbers.parseEntierOuRenvoi(cols.get(row, HAUTE_GUINEE), HAUTE_GUINEE))
                    .guineeForestiere(Numbers.parseEntierOuRenvoi(cols.get(row, GUINEE_FORESTIERE), GUINEE_FORESTIERE))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
