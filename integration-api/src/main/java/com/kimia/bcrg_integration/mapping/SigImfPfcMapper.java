package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfDonnePortefeuilleCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.V.PFC} → items {@link SigImfDonnePortefeuilleCredit}
 * (endpoint {@code /api/sigimf/m5pfc}, DataModel {@code DataModelImfSigImfDonnePortefeuilleCreditString}).
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code Montant→montant} (numérique FR).
 */
@Component
public class SigImfPfcMapper implements SheetMapper<SigImfDonnePortefeuilleCredit> {

    @Override
    public List<SigImfDonnePortefeuilleCredit> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", "ELEMENT", "Montant");
        List<SigImfDonnePortefeuilleCredit> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;
            items.add(new SigImfDonnePortefeuilleCredit()
                    .code(code)
                    .element(cols.get(row, "ELEMENT"))
                    .montant(Numbers.parseFrenchDouble(cols.get(row, "Montant"), "Montant")));
        }
        return items;
    }
}
