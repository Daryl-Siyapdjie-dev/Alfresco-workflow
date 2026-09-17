package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfTauxProvision;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XVII.PROV} → items {@link SigImfTauxProvision}
 * (endpoint {@code /api/sigimf/m17prov}).
 * <p>Correspondance 1:1 : {@code CODE→code}, {@code ELEMENT→element}, {@code N° Compte→nCompte},
 * {@code LIBELLE→libelle}, {@code Montant en souffrance→montantSouffrance}, {@code Norme→norme},
 * {@code Provisions→provisions}.
 * <p>La feuille aère ses données d'une ligne vide avant la ligne de ratio finale : elle se lit donc
 * avec {@code skipBlankRows} (voir {@code SheetLayout}), sans quoi la dernière ligne serait perdue.
 * <p>Champ DTO sans source : {@code numeroLigne}.
 */
@Component
public class SigImfTauxProvisionMapper implements SheetMapper<SigImfTauxProvision> {

    private static final String N_COMPTE = "N° Compte";
    private static final String SOUFFRANCE = "Montant en souffrance";

    @Override
    public List<SigImfTauxProvision> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "CODE", "ELEMENT", N_COMPTE, "LIBELLE", SOUFFRANCE, "Norme", "Provisions");
        List<SigImfTauxProvision> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;   // ligne sans code = non significative
            items.add(new SigImfTauxProvision()
                    .code(code)
                    .element(cols.get(row, "ELEMENT"))
                    .nCompte(Numbers.parseFrenchDouble(cols.get(row, N_COMPTE), N_COMPTE))
                    .libelle(cols.get(row, "LIBELLE"))
                    .montantSouffrance(Numbers.parseFrenchDouble(cols.get(row, SOUFFRANCE), SOUFFRANCE))
                    .norme(Numbers.parseFrenchDouble(cols.get(row, "Norme"), "Norme"))
                    .provisions(Numbers.parseFrenchDouble(cols.get(row, "Provisions"), "Provisions")));
        }
        return items;
    }
}
