package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRepartitionPCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XI.RPCS} (répartition du portefeuille de crédit par secteur)
 * → items {@link SigImfRepartitionPCredit} (endpoint {@code /api/sigimf/m11rpcs}).
 * <p>Feuille <strong>matricielle</strong> : une ligne par population (non-résidents, ménages, genre,
 * région…), une colonne par secteur d'activité. Les six colonnes sectorielles alimentent les six
 * champs de même nom du DTO, {@code TOTAL→total}.
 * <p>Les intitulés de colonnes du template alignent leur libellé avec plusieurs espaces
 * (« Agriculture Elevage    Pêche ») : {@link Columns} normalise ces suites d'espaces, la
 * correspondance reste donc faite <em>par nom</em>. La feuille aère ses groupes de lignes avec des
 * lignes vides → lecture avec {@code skipBlankRows}.
 * <p>Champ DTO sans source : {@code numeroLigne}.
 */
@Component
public class SigImfRepartitionPCreditMapper implements SheetMapper<SigImfRepartitionPCredit> {

    private static final String AGRI = "Agriculture Elevage Pêche";
    private static final String TP = "Travaux publics Bâtiments Logements";
    private static final String COMMERCE = "Commerce Restaurant Hôtellerie";
    private static final String INDUSTRIE = "Industrie Artisanat";
    private static final String TRANSPORT = "Transport Communication";

    @Override
    public List<SigImfRepartitionPCredit> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "CODE", "ELEMENT", AGRI, TP, COMMERCE, INDUSTRIE, TRANSPORT, "Autres", "TOTAL");
        List<SigImfRepartitionPCredit> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;   // ligne de titre de sous-bloc ou ligne vide
            items.add(new SigImfRepartitionPCredit()
                    .code(code)
                    .element(cols.get(row, "ELEMENT"))
                    .agriElevage(Numbers.parseFrenchDouble(cols.get(row, AGRI), AGRI))
                    .travauxPublics(Numbers.parseFrenchDouble(cols.get(row, TP), TP))
                    .commerceRestaurant(Numbers.parseFrenchDouble(cols.get(row, COMMERCE), COMMERCE))
                    .industrieArtisanat(Numbers.parseFrenchDouble(cols.get(row, INDUSTRIE), INDUSTRIE))
                    .transportCommunication(Numbers.parseFrenchDouble(cols.get(row, TRANSPORT), TRANSPORT))
                    .autres(Numbers.parseFrenchDouble(cols.get(row, "Autres"), "Autres"))
                    .total(Numbers.parseFrenchDouble(cols.get(row, "TOTAL"), "TOTAL")));
        }
        return items;
    }
}
