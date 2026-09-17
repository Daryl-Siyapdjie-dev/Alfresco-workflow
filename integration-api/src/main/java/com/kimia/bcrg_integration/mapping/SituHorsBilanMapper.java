package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SituationHorsBilanImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code SITU_03} (situation — hors bilan) → items {@link SituationHorsBilanImf}
 * (endpoint {@code /api/imf/situ/situ03}, DataModel {@code DataModelImfSituationHorsBilanImfString}).
 * <p>Colonnes : {@code N° Compte→getnCompte}, {@code HORS BILAN (1)→libelle},
 * {@code RESIDENT→residentGnf}, {@code NON-RESIDENT→nonResidentGnf}, {@code TOTAL→total}.
 * <p>Feuille d'un seul bloc (contrairement à SITU_01/02), mais même règle de ligne : <strong>une
 * ligne compte si elle porte un n° de compte</strong>. Noter le singulier des intitulés
 * (« RESIDENT »), là où SITU_01/02 écrivent « RESIDENTS ».
 * <p>Champs cible sans source dans le template : {@code code} (colonne « N°CODE » vide) et
 * {@code valeurTotalLigne}.
 */
@Component
public class SituHorsBilanMapper implements SheetMapper<SituationHorsBilanImf> {

    static final String CODE = "N°CODE";
    static final String COMPTE = "N° Compte";
    static final String LIBELLE = "HORS BILAN (1)";
    static final String RESIDENT = "RESIDENT";
    static final String NON_RESIDENT = "NON-RESIDENT";
    static final String TOTAL = "TOTAL";

    @Override
    public List<SituationHorsBilanImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), COMPTE, LIBELLE, RESIDENT, NON_RESIDENT, TOTAL);
        List<SituationHorsBilanImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String compte = cols.get(row, COMPTE);
            if (compte.isEmpty()) continue;
            String code = cols.get(row, CODE);
            items.add(new SituationHorsBilanImf()
                    .code(code.isEmpty() ? null : code)
                    .getnCompte(compte)
                    .libelle(cols.get(row, LIBELLE))
                    .residentGnf(Numbers.parseFrenchDouble(cols.get(row, RESIDENT), RESIDENT))
                    .nonResidentGnf(Numbers.parseFrenchDouble(cols.get(row, NON_RESIDENT), NON_RESIDENT))
                    .total(Numbers.parseFrenchDouble(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
