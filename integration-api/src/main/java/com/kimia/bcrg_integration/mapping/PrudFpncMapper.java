package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CalculFondsPropresNetsCorriges;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code FPNC} (calcul des fonds propres nets corrigés) → items
 * {@link CalculFondsPropresNetsCorriges} (endpoint {@code /api/imf/prud/fpnc}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N°→numero}, {@code Poste→poste},
 * {@code Montant (1)→montantUn}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 */
@Component
public class PrudFpncMapper implements SheetMapper<CalculFondsPropresNetsCorriges> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String POSTE = "Poste";
    static final String MONTANT_UN = "Montant (1)";

    @Override
    public List<CalculFondsPropresNetsCorriges> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUMERO, POSTE, MONTANT_UN);
        List<CalculFondsPropresNetsCorriges> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new CalculFondsPropresNetsCorriges()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .poste(cols.get(row, POSTE))
                    .montantUn(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT_UN), MONTANT_UN)));
        }
        return items;
    }
}
