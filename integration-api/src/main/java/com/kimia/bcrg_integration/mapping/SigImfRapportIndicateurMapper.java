package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRapportIndicateur;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper <strong>mutualisé</strong> des quatre feuilles d'indicateurs SIGIMF, qui partagent la même
 * forme et le même DTO {@link SigImfRapportIndicateur} :
 * <ul>
 *   <li>{@code M.XXII.INDIC.PRUDENTIELS} → {@code /api/sigimf/m22indicprudentiels}</li>
 *   <li>{@code M.XXII.RATIO.PRUDENTIELS} → {@code /api/sigimf/m22ratioprudentiels}</li>
 *   <li>{@code TAB_AUTRES_INDIC_1} → {@code /api/sigimf/tabautresindic1}</li>
 *   <li>{@code TAB_AUTRES_INDIC_2} → {@code /api/sigimf/tabautresindic2}</li>
 * </ul>
 * <p>En-tête {@code N°.} : {@code N°.→nCompte} (le DTO n'a pas de champ {@code code} ; la colonne
 * porte les identifiants {@code M.IND.IM.1}, {@code M.RTP.IM.1}, {@code M.AI1.IM.1}…),
 * {@code LIBELLE→libelle}, {@code RATIOS / VALEURS→montant}.
 * <p>La 3e colonne s'intitule {@code FORMULES} sur la feuille des indicateurs et {@code NORMES} sur
 * les trois autres ; elle occupe la même position et alimente {@code formules} dans les deux cas —
 * le DTO n'ayant pas de champ « norme », <strong>c'est un écart à confirmer</strong> avec la matrice
 * de correspondance.
 * <p>La ligne de pied {@code Total | N/A | N/A | N/A} du template est ignorée (elle ne porte pas
 * d'identifiant et ses valeurs ne sont pas numériques). Les agrégats intermédiaires situés plus bas
 * dans ces feuilles sont hors du tableau (séparés par une ligne vide) et ne sont donc pas lus.
 * <p>Champs DTO sans source : {@code categorie}, {@code numeroLigne}, {@code section} (énum A…F).
 */
@Component
public class SigImfRapportIndicateurMapper implements SheetMapper<SigImfRapportIndicateur> {

    /** Marqueur d'en-tête de ces quatre feuilles. */
    public static final String HEADER_MARKER = "N°.";

    private static final String RATIOS = "RATIOS / VALEURS";
    private static final String TOTAL = "total";

    @Override
    public List<SigImfRapportIndicateur> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), HEADER_MARKER, "LIBELLE", RATIOS);
        List<SigImfRapportIndicateur> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String numero = cols.get(row, HEADER_MARKER);
            if (numero.isEmpty() || numero.trim().equalsIgnoreCase(TOTAL)) continue;   // ligne de pied
            String formules = cols.get(row, "FORMULES");
            if (formules.isEmpty()) formules = cols.get(row, "NORMES");   // même colonne, autre intitulé
            items.add(new SigImfRapportIndicateur()
                    .nCompte(numero)
                    .libelle(cols.get(row, "LIBELLE"))
                    .formules(formules)
                    .montant(Numbers.parseFrenchDouble(cols.get(row, RATIOS), RATIOS)));
        }
        return items;
    }
}
