package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCompteResultat;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper d'un <strong>côté</strong> du compte de résultat SIGIMF {@code M.II.RESULTAT} → items
 * {@link SigImfCompteResultat} (endpoint {@code /api/sigimf/m2resultat}, DataModel
 * {@code DataModelImfSigImfCompteResultatString}).
 *
 * <p>Même forme que {@link SigImfBilanMapper} : la feuille empile les charges puis les produits sous
 * un second en-tête {@code CODE}, la colonne de libellé s'intitulant {@code CHARGES} puis
 * {@code PRODUITS}. Le booléen {@code product} du DTO n'a pas de colonne source — c'est le bloc
 * d'où vient la ligne qui le détermine.
 *
 * <p>Colonnes : {@code CODE→code}, {@code N° compte→nCompte}, {@code CHARGES|PRODUITS→libelle},
 * {@code Montant→montant}.
 */
@Component
public class SigImfCompteResultatMapper implements SheetMapper<SigImfCompteResultat> {

    /** Libellé de la colonne de désignation du bloc des charges. */
    public static final String COL_CHARGES = "CHARGES";
    /** Libellé de la même colonne pour le bloc des produits, qui ouvre le second tableau. */
    public static final String COL_PRODUITS = "PRODUITS";

    private final String colonneLibelle;
    private final boolean produit;

    /** Bloc des charges — forme par défaut, conservée pour l'injection Spring et les tests. */
    public SigImfCompteResultatMapper() {
        this(COL_CHARGES, false);
    }

    public SigImfCompteResultatMapper(String colonneLibelle, boolean produit) {
        this.colonneLibelle = colonneLibelle;
        this.produit = produit;
    }

    @Override
    public List<SigImfCompteResultat> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", "N° compte", colonneLibelle, "Montant");
        List<SigImfCompteResultat> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;
            items.add(new SigImfCompteResultat()
                    .code(code)
                    .product(produit)
                    .nCompte(cols.get(row, "N° compte"))
                    .libelle(cols.get(row, colonneLibelle))
                    .montant(Numbers.parseFrenchDouble(cols.get(row, "Montant"), "Montant")));
        }
        return items;
    }
}
