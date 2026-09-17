package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.RepartitionEncoursCredits;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_10} (répartition de l'encours des crédits par secteur) → items
 * {@link RepartitionEncoursCredits} (endpoint {@code /api/imf/igec/igec10}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>Les colonnes sectorielles sont des <em>entiers</em> côté DTO et le total un décimal : c'est la
 * cible qui l'impose, la source ne distingue pas.
 */
@Component
public class IgecEncoursCreditsMapper implements SheetMapper<RepartitionEncoursCredits> {

    static final String CODE = "N°CODE";
    static final String DENOMINATION = "Dénomination complète";
    static final String AGRICULTURE = "Agriculture, Elevage, Pêche";
    static final String TRAVAUX_PUBLICS = "Travaux publics, Bâtiments, Logements";
    static final String COMMERCE = "Commerce, Restaurant, Hotellerie";
    static final String INDUSTRIE = "Industrie, Artisanat";
    static final String AUTRES = "Autres";
    static final String TOTAL = "Total";

    @Override
    public List<RepartitionEncoursCredits> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DENOMINATION, AGRICULTURE, TOTAL);
        List<RepartitionEncoursCredits> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new RepartitionEncoursCredits()
                    .code(code)
                    .denominationComplete(cols.get(row, DENOMINATION))
                    .agricultureElevagePeche(Numbers.parseEntierOuRenvoi(cols.get(row, AGRICULTURE), AGRICULTURE))
                    .travauxPublicsBatimentsLogement(Numbers.parseEntierOuRenvoi(cols.get(row, TRAVAUX_PUBLICS), TRAVAUX_PUBLICS))
                    .commerceRestaurantHotellerie(Numbers.parseEntierOuRenvoi(cols.get(row, COMMERCE), COMMERCE))
                    .industrieArtisanat(Numbers.parseEntierOuRenvoi(cols.get(row, INDUSTRIE), INDUSTRIE))
                    .autres(Numbers.parseEntierOuRenvoi(cols.get(row, AUTRES), AUTRES))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
