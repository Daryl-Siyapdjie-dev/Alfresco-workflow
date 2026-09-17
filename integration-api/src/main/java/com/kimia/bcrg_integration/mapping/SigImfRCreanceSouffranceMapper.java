package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRCreanceSouffrance;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XII.RCS} (recouvrement des créances en souffrance radiées)
 * → items {@link SigImfRCreanceSouffrance} (endpoint {@code /api/sigimf/m12rcs}).
 * <p>Feuille à <strong>en-tête sur deux lignes</strong> : « RECOUVREMENT AU COURS DE L'EXERCICE »
 * chapeaute quatre colonnes précisées en dessous (« 1er trimestre »…). Elle se lit donc avec un
 * {@code headerRowSpan} de 2 (voir {@code SheetLayout}), qui retient l'intitulé le plus précis.
 * <p>La 3e ligne d'en-tête du template (« Principal » répété) n'a pas de code : elle est ignorée
 * comme n'importe quelle ligne sans {@code CODE}.
 */
@Component
public class SigImfRCreanceSouffranceMapper implements SheetMapper<SigImfRCreanceSouffrance> {

    private static final String TRANCHE = "Tranches de jours de retard";
    private static final String SOLDE = "Solde des crédits au 31 décembre précédent";
    private static final String T1 = "1er trimestre";
    private static final String T2 = "2ème trimestre";
    private static final String T3 = "3ème trimestre";
    private static final String T4 = "4ème trimestre";

    @Override
    public List<SigImfRCreanceSouffrance> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", TRANCHE, SOLDE, T1, T2, T3, T4, "TOTAL");
        List<SigImfRCreanceSouffrance> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;   // 3e ligne d'en-tête (« Principal ») ou ligne vide
            items.add(new SigImfRCreanceSouffrance()
                    .code(code)
                    .trancheJourRetard(cols.get(row, TRANCHE))
                    .soldeCreditPrecedent(Numbers.parseFrenchDouble(cols.get(row, SOLDE), SOLDE))
                    .trimestre1(Numbers.parseFrenchDouble(cols.get(row, T1), T1))
                    .trimestre2(Numbers.parseFrenchDouble(cols.get(row, T2), T2))
                    .trimestre3(Numbers.parseFrenchDouble(cols.get(row, T3), T3))
                    .trimestre4(Numbers.parseFrenchDouble(cols.get(row, T4), T4))
                    .total(Numbers.parseFrenchDouble(cols.get(row, "TOTAL"), "TOTAL")));
        }
        return items;
    }
}
