package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CouvertureEmploisStables;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code PRUD_03} (couverture des emplois stables) → items
 * {@link CouvertureEmploisStables} (endpoint {@code /api/imf/prud/prud_03}).
 * <p>Colonnes : {@code N° CODE→code}, {@code N° Compte→numCompte}, {@code Composition→libelle},
 * {@code Montant→montant}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>Le marqueur d'en-tête s'écrit ici « N° CODE » (avec une espace) : le lecteur compare les
 * intitulés sans tenir compte des espaces, les deux graphies désignent la même colonne.
 */
@Component
public class PrudEmploisStablesMapper implements SheetMapper<CouvertureEmploisStables> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° Compte";
    static final String LIBELLE = "Composition";
    static final String MONTANT = "Montant";

    @Override
    public List<CouvertureEmploisStables> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUM_COMPTE, LIBELLE, MONTANT);
        List<CouvertureEmploisStables> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new CouvertureEmploisStables()
                    .code(code)
                    .numCompte(cols.get(row, NUM_COMPTE))
                    .libelle(cols.get(row, LIBELLE))
                    .montant(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT)));
        }
        return items;
    }
}
