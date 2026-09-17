package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CalculFondsPropresNets;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code FPN} (calcul des fonds propres nets) → items
 * {@link CalculFondsPropresNets} (endpoint {@code /api/imf/prud/fpn}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N° Compte→numCompte}, {@code Poste→poste},
 * {@code Montant→montant}, {@code Pondération→ponderation}, {@code Montant (1)→montantUn}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>La colonne « Montant » du template porte, sur plusieurs lignes, un renvoi vers la feuille où
 * lire la valeur (« IGEC_18: AT13 ») : consigne de remplissage, traitée comme une case vide.
 */
@Component
public class PrudFpnMapper implements SheetMapper<CalculFondsPropresNets> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° Compte";
    static final String POSTE = "Poste";
    static final String MONTANT = "Montant";
    static final String PONDERATION = "Pondération";
    static final String MONTANT_UN = "Montant (1)";

    @Override
    public List<CalculFondsPropresNets> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), POSTE, MONTANT, MONTANT_UN);
        List<CalculFondsPropresNets> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new CalculFondsPropresNets()
                    .code(code)
                    .numCompte(cols.get(row, NUM_COMPTE))
                    .poste(cols.get(row, POSTE))
                    .montant(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT))
                    .ponderation(Numbers.parseMontantOuRenvoi(cols.get(row, PONDERATION), PONDERATION))
                    .montantUn(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT_UN), MONTANT_UN)));
        }
        return items;
    }
}
