package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.RisquesCredits;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code APR} (actifs pondérés des risques de crédit) → items
 * {@link RisquesCredits} (endpoint {@code /api/imf/prud/apr}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N° Compte→numCompte}, {@code Poste→poste},
 * {@code Exposition nette→expositionNette}, {@code Coefficient de pondération→coefficientPonderation},
 * {@code Actifs pondérés des risques→actifsPonderes}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>En-tête lu sur <strong>une seule ligne</strong> : la ligne suivante ne numérote que les colonnes
 * (« (1) », « (2) », « (3) = 1 x 2 ») et l'inclure dans l'en-tête remplacerait les vrais intitulés.
 * Sans code, elle est écartée comme les autres lignes de service.
 * <p>La colonne « Exposition nette » porte parfois une formule en clair
 * (« COMPTES (30+31+32+33)- COMPTES (390+391+394) ») : consigne, traitée comme une case vide.
 */
@Component
public class PrudAprMapper implements SheetMapper<RisquesCredits> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° Compte";
    static final String POSTE = "Poste";
    static final String EXPOSITION = "Exposition nette";
    static final String COEFFICIENT = "Coefficient de pondération";
    static final String ACTIFS_PONDERES = "Actifs pondérés des risques";

    @Override
    public List<RisquesCredits> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), POSTE, EXPOSITION, COEFFICIENT, ACTIFS_PONDERES);
        List<RisquesCredits> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new RisquesCredits()
                    .code(code)
                    .numCompte(cols.get(row, NUM_COMPTE))
                    .poste(cols.get(row, POSTE))
                    .expositionNette(Numbers.parseMontantOuRenvoi(cols.get(row, EXPOSITION), EXPOSITION))
                    .coefficientPonderation(Numbers.parseMontantOuRenvoi(cols.get(row, COEFFICIENT), COEFFICIENT))
                    .actifsPonderes(Numbers.parseMontantOuRenvoi(cols.get(row, ACTIFS_PONDERES), ACTIFS_PONDERES)));
        }
        return items;
    }
}
