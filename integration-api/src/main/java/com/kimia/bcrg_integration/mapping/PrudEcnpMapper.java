package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ConformiteNormesPrudentielles;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code ECNP} (état de conformité aux normes prudentielles) → items
 * {@link ConformiteNormesPrudentielles} (endpoint {@code /api/imf/prud/ecnp}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N°→numero}, {@code LISTE DES NORMES PRUDENTIELLES→norme},
 * {@code Niveau à respecter (1)→niveauRepecter}, {@code Niveau observé (2)→niveauObserve}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>La colonne « Niveau observé » porte parfois une consigne (« Nombre de dossiers en
 * infraction(PRUD_04) ») au lieu d'un taux : traitée comme une case vide
 * ({@link Numbers#parseMontantOuRenvoi}).
 */
@Component
public class PrudEcnpMapper implements SheetMapper<ConformiteNormesPrudentielles> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String NORME = "LISTE DES NORMES PRUDENTIELLES";
    static final String A_RESPECTER = "Niveau à respecter (1)";
    static final String OBSERVE = "Niveau observé (2)";

    @Override
    public List<ConformiteNormesPrudentielles> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUMERO, NORME);
        List<ConformiteNormesPrudentielles> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new ConformiteNormesPrudentielles()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .norme(cols.get(row, NORME))
                    .niveauRepecter(Numbers.parseMontantOuRenvoi(cols.get(row, A_RESPECTER), A_RESPECTER))
                    .niveauObserve(Numbers.parseMontantOuRenvoi(cols.get(row, OBSERVE), OBSERVE)));
        }
        return items;
    }
}
