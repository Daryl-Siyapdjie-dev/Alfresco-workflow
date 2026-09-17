package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CompteResultatProduit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>premier</strong> tableau de {@code FINS_09} — les <em>produits</em> du compte
 * de résultat → items {@link CompteResultatProduit} (endpoint {@code /api/imf/fins/fins09}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong>
 * <p>Cette feuille empile <strong>deux tableaux</strong> de natures différentes ; celui-ci est
 * le premier. Sa lecture est bornée par la forme déclarée au registre — sans quoi le premier
 * tableau avalerait les lignes du second, qui partiraient alors comme des produits.
 */
@Component
public class FinsCompteResultatMapper implements SheetMapper<CompteResultatProduit> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° Compte";
    static final String LIBELLE = "ELEMENTS";
    static final String SOLDE = "SOLDE (N)";
    static final String POURCENTAGE = "%";

    @Override
    public List<CompteResultatProduit> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, SOLDE);
        List<CompteResultatProduit> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code, sheet.sheetName())) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CompteResultatProduit()
                    .code(code)
                    .numCpte(cols.get(row, NUM_COMPTE))
                    .libelle(cols.get(row, LIBELLE))
                    .soldeGnf(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE), SOLDE))
                    .pourcentageGnf(Numbers.parseMontantOuRenvoi(cols.get(row, POURCENTAGE), POURCENTAGE)));
        }
        return items;
    }
}
