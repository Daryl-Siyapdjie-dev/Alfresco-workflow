package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SituationBilanPassifImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code SITU_02} (situation — passif) → items {@link SituationBilanPassifImf}
 * (endpoint {@code /api/imf/situ/situ02}, DataModel {@code DataModelImfSituationBilanPassifImfString}).
 * <p>Colonnes : {@code N° Compte→getnCOmpte}, {@code PASSIF (1)→libelle},
 * {@code RESIDENTS→residentGnf}, {@code NON-RESIDENTS→nonResidentGnf}, {@code TOTAL→total}.
 * <p>Même forme en deux blocs que {@link SituActifMapper} (« PASSIF (1) » puis « PASSIF (2) »), et
 * même règle : <strong>une ligne compte si elle porte un n° de compte</strong>.
 * <p>Le nom {@code getnCOmpte} vient de la spec OpenAPI de SIRYF (getter mal nommé côté serveur) ;
 * il est conservé tel quel pour rester aligné sur les DTO générés.
 * <p>Champs cible sans source dans le template : {@code code} (colonne « N°CODE » vide) et
 * {@code valeurTotalLigne}.
 */
@Component
public class SituPassifMapper implements SheetMapper<SituationBilanPassifImf> {

    static final String CODE = "N°CODE";
    static final String COMPTE = "N° Compte";
    static final String LIBELLE = "PASSIF (1)";
    static final String RESIDENTS = "RESIDENTS";
    static final String NON_RESIDENTS = "NON-RESIDENTS";
    static final String TOTAL = "TOTAL";

    @Override
    public List<SituationBilanPassifImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), COMPTE, LIBELLE, RESIDENTS, NON_RESIDENTS, TOTAL);
        List<SituationBilanPassifImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String compte = cols.get(row, COMPTE);
            if (compte.isEmpty()) continue;
            String code = cols.get(row, CODE);
            items.add(new SituationBilanPassifImf()
                    .code(code.isEmpty() ? null : code)
                    .getnCOmpte(compte)
                    .libelle(cols.get(row, LIBELLE))
                    .residentGnf(Numbers.parseFrenchDouble(cols.get(row, RESIDENTS), RESIDENTS))
                    .nonResidentGnf(Numbers.parseFrenchDouble(cols.get(row, NON_RESIDENTS), NON_RESIDENTS))
                    .total(Numbers.parseFrenchDouble(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
