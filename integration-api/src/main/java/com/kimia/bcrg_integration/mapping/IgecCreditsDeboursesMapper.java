package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.RepartitionCreditsDebourses;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_12} (crédits déboursés dans l'année, par secteur) → items
 * {@link RepartitionCreditsDebourses} (endpoint {@code /api/imf/igec/igec12}).
 * <p>Même disposition qu'{@code IGEC_10}, mais un DTO et un endpoint distincts : deux mappers plutôt
 * qu'un mapper générique, la duplication étant ici plus lisible que l'abstraction.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecCreditsDeboursesMapper implements SheetMapper<RepartitionCreditsDebourses> {

    static final String CODE = "N°CODE";
    static final String DENOMINATION = "Dénomination complète";
    static final String AGRICULTURE = "Agriculture, Elevage, Pêche";
    static final String TRAVAUX_PUBLICS = "Travaux publics, Bâtiments, Logements";
    static final String COMMERCE = "Commerce, Restaurant, Hotellerie";
    static final String INDUSTRIE = "Industrie, Artisanat";
    static final String AUTRES = "Autres";
    static final String TOTAL = "Total";

    @Override
    public List<RepartitionCreditsDebourses> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DENOMINATION, AGRICULTURE, TOTAL);
        List<RepartitionCreditsDebourses> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new RepartitionCreditsDebourses()
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
