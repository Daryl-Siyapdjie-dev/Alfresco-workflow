package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.InformationCommissaireCompteImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_07} (commissaires aux comptes) → items
 * {@link InformationCommissaireCompteImf} (endpoint {@code /api/imf/igec/igec07}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>{@code effectif} est un champ <em>texte</em> côté DTO : la valeur est reprise telle quelle.
 */
@Component
public class IgecCommissairesMapper implements SheetMapper<InformationCommissaireCompteImf> {

    static final String CODE = "N°CODE";
    static final String RAISON_SOCIALE = "NOM OU RAISON SOCIALE";
    static final String REPRESENTE = "REPRESENTEE PAR";
    static final String FONCTION = "Fonction (*)";
    static final String EFFECTIF = "Effectif";
    static final String AUDITEURS = "Auditeurs séniors";
    static final String RAE = "Référence d'inscription au R A E (1)";
    static final String TOE = "Référence d'inscription au T O E C (2)";
    static final String AUTORISATION = "Référence de l'autorisation d'exercice de la fonction de CAC";

    @Override
    public List<InformationCommissaireCompteImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), RAISON_SOCIALE, REPRESENTE, FONCTION);
        List<InformationCommissaireCompteImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new InformationCommissaireCompteImf()
                    .code(code)
                    .nomRaisonSociale(cols.get(row, RAISON_SOCIALE))
                    .representePar(cols.get(row, REPRESENTE))
                    .fonction(cols.get(row, FONCTION))
                    .effectif(cols.get(row, EFFECTIF))
                    .auditeursSeniors(cols.get(row, AUDITEURS))
                    .referenceInscriptionRAE(cols.get(row, RAE))
                    .referenceInscriptionTOE(cols.get(row, TOE))
                    .referenceAutorisationExerciceCAC(cols.get(row, AUTORISATION)));
        }
        return items;
    }
}
