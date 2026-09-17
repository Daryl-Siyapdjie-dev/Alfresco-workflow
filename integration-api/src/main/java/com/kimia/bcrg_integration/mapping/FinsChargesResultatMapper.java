package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CompteResultatCharges;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>second</strong> tableau de {@code FINS_09} — les <em>charges</em> du compte de
 * résultat → items {@link CompteResultatCharges}, déposés dans la liste {@code items2} du même envoi.
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le second. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des produits.
 * <p>Son en-tête est écrit « CODE » (et non « N°CODE ») et n'intitule pas la colonne des numéros de
 * compte, pourtant renseignée : celle-ci est donc <strong>lue par position</strong> ({@code col4}).
 */
@Component
public class FinsChargesResultatMapper implements SheetMapper<CompteResultatCharges> {

    static final String CODE = "CODE";
    static final String NUM_COMPTE = "col4";
    static final String LIBELLE = "ELEMENTS";
    static final String SOLDE = "SOLDE";
    static final String POURCENTAGE = "%";

    @Override
    public List<CompteResultatCharges> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, SOLDE);
        List<CompteResultatCharges> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CompteResultatCharges()
                    .code(code)
                    .numCpte(cols.get(row, NUM_COMPTE))
                    .libelle(cols.get(row, LIBELLE))
                    .soldeGnf(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE), SOLDE))
                    .pourcentageGnf(Numbers.parseMontantOuRenvoi(cols.get(row, POURCENTAGE), POURCENTAGE)));
        }
        return items;
    }
}
