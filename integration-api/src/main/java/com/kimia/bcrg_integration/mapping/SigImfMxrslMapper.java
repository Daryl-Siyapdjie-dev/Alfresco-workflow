package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfMxrsl;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.X.RSL} → items {@link SigImfMxrsl}
 * (endpoint {@code /api/sigimf/m10rsl}, DataModel {@code DataModelImfSigImfMxrslString}).
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code N° Compte→nCompte},
 * {@code Autres engagements reçus→libelle}, {@code MONTANT GNF→montantGnf},
 * {@code TAUX DE PONDERATION→tauxPonderation} (texte, conservé tel quel), {@code MONTANT RETENU→montantRetenu}.
 * <p>Le libellé est porté par une colonne dont l'intitulé peut varier selon la section
 * (« Autres engagements reçus » dans l'échantillon) → à confirmer avec la matrice de correspondance.
 */
@Component
public class SigImfMxrslMapper implements SheetMapper<SigImfMxrsl> {

    private static final String COL_LIBELLE = "Autres engagements reçus";

    @Override
    public List<SigImfMxrsl> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "CODE", "ELEMENT", "N° Compte", "MONTANT GNF", "TAUX DE PONDERATION", "MONTANT RETENU");
        List<SigImfMxrsl> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;
            String taux = cols.get(row, "TAUX DE PONDERATION");
            items.add(new SigImfMxrsl()
                    .code(code)
                    .element(cols.get(row, "ELEMENT"))
                    .nCompte(Numbers.parseFrenchDouble(cols.get(row, "N° Compte"), "N° Compte"))
                    .libelle(cols.get(row, COL_LIBELLE))
                    .montantGnf(Numbers.parseFrenchDouble(cols.get(row, "MONTANT GNF"), "MONTANT GNF"))
                    .tauxPonderation(taux.isEmpty() ? null : taux)
                    .montantRetenu(Numbers.parseFrenchDouble(cols.get(row, "MONTANT RETENU"), "MONTANT RETENU")));
        }
        return items;
    }
}
