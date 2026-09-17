package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CalculRatioSolvabiliteImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code PRUD_01} (calcul des ratios de solvabilité) → items
 * {@link CalculRatioSolvabiliteImf} (endpoint {@code /api/imf/prud/prud_01}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N°→numero}, {@code Poste→poste},
 * {@code Référence→reference}, {@code Niveau /Montant estimé (1)→montant}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>La colonne des repères « (a) »…« (e) », en fin de ligne, n'a pas de champ cible : écart assumé,
 * à porter à la matrice de correspondance.
 */
@Component
public class PrudRatioSolvabiliteMapper implements SheetMapper<CalculRatioSolvabiliteImf> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String POSTE = "Poste";
    static final String REFERENCE = "Référence";
    static final String MONTANT = "Niveau /Montant estimé (1)";

    @Override
    public List<CalculRatioSolvabiliteImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUMERO, POSTE, MONTANT);
        List<CalculRatioSolvabiliteImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new CalculRatioSolvabiliteImf()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .poste(cols.get(row, POSTE))
                    .reference(cols.get(row, REFERENCE))
                    .montant(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT)));
        }
        return items;
    }
}
