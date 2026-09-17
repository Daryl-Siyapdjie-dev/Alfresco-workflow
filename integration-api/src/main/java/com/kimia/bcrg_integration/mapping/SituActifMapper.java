package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SituationBilanActif;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code SITU_01} (situation — actif) → items {@link SituationBilanActif}
 * (endpoint {@code /api/imf/situ/situ01}, DataModel {@code DataModelImfSituationBilanActifString}).
 * <p>Colonnes : {@code N° compte→numCompte}, {@code ACTIF (1)→libelle},
 * {@code PROVISIONS ET AMORTISSEMTS→provisionAmortissement}, {@code RESIDENTS→resident},
 * {@code NON-RESIDENTS→nonResident}, {@code ACTIF NET→actifNet}.
 * <p>La feuille tient sur <strong>deux blocs</strong> (« ACTIF (1) » puis « ACTIF (2) », de mêmes
 * colonnes) : le lecteur les enchaîne en une passe et les lignes du second bloc retombent sur les
 * intitulés du premier — d'où un seul mapper et un seul envoi.
 * <p><strong>Ligne retenue = ligne portant un n° de compte.</strong> C'est ce qui écarte, sans les
 * énumérer, les lignes de titre du 2e bloc, la ligne de numérotation des colonnes (« 1 2 3 4 ») et
 * la ligne « TOTAL ACTIF » — aucune ne décrit un poste de la situation.
 * <p>Champs cible sans source dans le template — laissés à {@code null} : {@code code} (la colonne
 * « N°CODE » existe mais est vide, cf. BILAN §1.5), {@code total} et {@code valeurTotalLigne}.
 */
@Component
public class SituActifMapper implements SheetMapper<SituationBilanActif> {

    static final String CODE = "N°CODE";
    static final String COMPTE = "N° compte";
    static final String LIBELLE = "ACTIF (1)";
    static final String PROVISIONS = "PROVISIONS ET AMORTISSEMTS";
    static final String RESIDENTS = "RESIDENTS";
    static final String NON_RESIDENTS = "NON-RESIDENTS";
    static final String ACTIF_NET = "ACTIF NET";

    @Override
    public List<SituationBilanActif> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), COMPTE, LIBELLE, PROVISIONS, RESIDENTS, NON_RESIDENTS, ACTIF_NET);
        List<SituationBilanActif> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String compte = cols.get(row, COMPTE);
            if (compte.isEmpty()) continue;
            String code = cols.get(row, CODE);
            items.add(new SituationBilanActif()
                    .code(code.isEmpty() ? null : code)
                    .numCompte(compte)
                    .libelle(cols.get(row, LIBELLE))
                    .provisionAmortissement(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISIONS), PROVISIONS))
                    .resident(Numbers.parseFrenchDouble(cols.get(row, RESIDENTS), RESIDENTS))
                    .nonResident(Numbers.parseFrenchDouble(cols.get(row, NON_RESIDENTS), NON_RESIDENTS))
                    .actifNet(Numbers.parseFrenchDouble(cols.get(row, ACTIF_NET), ACTIF_NET)));
        }
        return items;
    }
}
