package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBilan;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper d'un <strong>côté</strong> du bilan SIGIMF {@code M.I.BILAN} → items {@link SigImfBilan}
 * (endpoint {@code /api/sigimf/m1bilan}, DataModel {@code DataModelImfSigImfBilanString}).
 *
 * <p>La feuille empile deux tableaux de colonnes identiques : l'actif, puis le passif sous un second
 * en-tête {@code CODE} dont la colonne de libellé s'intitule {@code PASSIF} au lieu de {@code ACTIF}.
 * Le mapper est donc paramétré par ce libellé et par la valeur du booléen {@code actif} du DTO, qui
 * est précisément ce qui distingue les deux côtés côté API — un item de passif n'a pas de colonne
 * source qui le dise, c'est sa position dans la feuille qui le dit.
 *
 * <p>Correspondance de colonnes : {@code CODE→code}, {@code N° compte→nCompte},
 * {@code ACTIF|PASSIF→libelle}, {@code Montant brut→montantBrut},
 * {@code Amortissement & Provisions→amortissementProvision}, {@code Montant net→montantNet}.
 */
@Component
public class SigImfBilanMapper implements SheetMapper<SigImfBilan> {

    /** Libellé de la colonne de désignation du côté actif, et marqueur de fin du bloc passif. */
    public static final String COL_ACTIF = "ACTIF";
    /** Libellé de la même colonne du côté passif : c'est lui qui ouvre le second tableau. */
    public static final String COL_PASSIF = "PASSIF";

    private final String colonneLibelle;
    private final boolean actif;

    /** Côté actif — forme par défaut, conservée pour l'injection Spring et les tests. */
    public SigImfBilanMapper() {
        this(COL_ACTIF, true);
    }

    public SigImfBilanMapper(String colonneLibelle, boolean actif) {
        this.colonneLibelle = colonneLibelle;
        this.actif = actif;
    }

    @Override
    public List<SigImfBilan> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "CODE", "N° compte", colonneLibelle, "Montant brut", "Amortissement & Provisions",
                "Montant net");
        List<SigImfBilan> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;   // ligne sans code = non significative
            items.add(new SigImfBilan()
                    .code(code)
                    .actif(actif)
                    .nCompte(cols.get(row, "N° compte"))
                    .libelle(cols.get(row, colonneLibelle))
                    .montantBrut(Numbers.parseFrenchDouble(cols.get(row, "Montant brut"), "Montant brut"))
                    .amortissementProvision(
                            Numbers.parseFrenchDouble(cols.get(row, "Amortissement & Provisions"),
                                    "Amortissement & Provisions"))
                    .montantNet(Numbers.parseFrenchDouble(cols.get(row, "Montant net"), "Montant net")));
        }
        return items;
    }
}
