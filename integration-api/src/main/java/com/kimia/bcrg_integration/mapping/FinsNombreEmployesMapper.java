package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.NombreEmployeImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>second</strong> tableau de {@code FINS_14} — le <em>nombre d'employés</em>,
 * ventilé par lieu et par sexe → items {@link NombreEmployeImf}, déposés dans {@code items2}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le second. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des charges.
 * <p>Trois couples « FEMME / HOMME » se suivent sous « SIEGE », « AGENCES » et « GENRE ». Les
 * intitulés étant homonymes, le lecteur les rend uniques dans l'ordre des colonnes : « FEMME »,
 * « FEMME (2) », « FEMME (3) » — soit siège, agences, genre. {@code valeurTotalLigne} n'a pas de source.
 */
@Component
public class FinsNombreEmployesMapper implements SheetMapper<NombreEmployeImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "LIBELLES";
    static final String FEMME_SIEGE = "FEMME";
    static final String HOMME_SIEGE = "HOMME";
    static final String FEMME_AGENCE = "FEMME (2)";
    static final String HOMME_AGENCE = "HOMME (2)";
    static final String FEMME_GENRE = "FEMME (3)";
    static final String HOMME_GENRE = "HOMME (3)";
    static final String EFFECTIF_TOTAL = "EFFECTIF TOTAL";

    @Override
    public List<NombreEmployeImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, FEMME_SIEGE, HOMME_SIEGE, EFFECTIF_TOTAL);
        List<NombreEmployeImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new NombreEmployeImf()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .femmeSiege(Numbers.parseEntierOuRenvoi(cols.get(row, FEMME_SIEGE), FEMME_SIEGE))
                    .hommeSiege(Numbers.parseEntierOuRenvoi(cols.get(row, HOMME_SIEGE), HOMME_SIEGE))
                    .femmeAgence(Numbers.parseEntierOuRenvoi(cols.get(row, FEMME_AGENCE), FEMME_AGENCE))
                    .hommeAgence(Numbers.parseEntierOuRenvoi(cols.get(row, HOMME_AGENCE), HOMME_AGENCE))
                    .femmeGenre(Numbers.parseEntierOuRenvoi(cols.get(row, FEMME_GENRE), FEMME_GENRE))
                    .hommeGenre(Numbers.parseEntierOuRenvoi(cols.get(row, HOMME_GENRE), HOMME_GENRE))
                    .effectifTotal(Numbers.parseEntierOuRenvoi(cols.get(row, EFFECTIF_TOTAL), EFFECTIF_TOTAL)));
        }
        return items;
    }
}
