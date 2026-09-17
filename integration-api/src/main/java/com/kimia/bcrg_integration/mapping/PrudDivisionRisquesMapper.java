package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.DivisionRisques;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code PRUD_04} (division des risques) → items {@link DivisionRisques}
 * (endpoint {@code /api/imf/prud/prud_04}).
 * <p>Colonnes : {@code N° CODE→code}, {@code N°→numero},
 * {@code Nom ou Raison Sociale du Bénéficiaire→nomBeneficiaire},
 * {@code Exposinettes nettes ou Risque par bénéficiaire→risqueNetBeneficiaire},
 * {@code Norme par Bénéficiaire→normeBeneficiaire},
 * {@code Ecart sur les engagements individuels…→ecartEngagementIndividuel}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>Feuille de <strong>liste</strong> : les lignes 1 à 13 sont pré-codées et vides dans le template
 * (un bénéficiaire par ligne, à remplir). Elles portent un code, donc elles partent — c'est bien
 * ce qu'attend SIRYF, qui raisonne par code de ligne. Le bloc « Pour mémoire » du haut (FPN, 10 %
 * des FPN, 8 fois les FPN) n'a pas de code : il reste à quai.
 */
@Component
public class PrudDivisionRisquesMapper implements SheetMapper<DivisionRisques> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String NOM = "Nom ou Raison Sociale du Bénéficiaire (Liste IGEC2)";
    static final String RISQUE = "Exposinettes nettes ou Risque par bénéficiaire";
    static final String NORME = "Norme par Bénéficiaire";
    static final String ECART = "Ecart sur les engagements individuels depassant 10% des FPN";

    @Override
    public List<DivisionRisques> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUMERO, NOM, RISQUE, NORME, ECART);
        List<DivisionRisques> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new DivisionRisques()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .nomBeneficiaire(cols.get(row, NOM))
                    .risqueNetBeneficiaire(Numbers.parseMontantOuRenvoi(cols.get(row, RISQUE), RISQUE))
                    .normeBeneficiaire(Numbers.parseMontantOuRenvoi(cols.get(row, NORME), NORME))
                    .ecartEngagementIndividuel(Numbers.parseMontantOuRenvoi(cols.get(row, ECART), ECART)));
        }
        return items;
    }
}
