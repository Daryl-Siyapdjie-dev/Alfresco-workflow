package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfHorsBilan;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.III.HS_BILAN} → items {@link SigImfHorsBilan}
 * (endpoint {@code /api/sigimf/m3horsbilan}, DataModel {@code DataModelImfSigImfHorsBilanString}).
 * <p>Colonnes : {@code CODE→code}, {@code N° compte→nCompte}, {@code LIBELLES→libelle},
 * {@code Montant→montant} (numérique, parseur FR mutualisé).
 */
@Component
public class SigImfHorsBilanMapper implements SheetMapper<SigImfHorsBilan> {

    @Override
    public List<SigImfHorsBilan> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", "N° compte", "LIBELLES", "Montant");
        List<SigImfHorsBilan> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;
            items.add(new SigImfHorsBilan()
                    .code(code)
                    .nCompte(cols.get(row, "N° compte"))
                    .libelle(cols.get(row, "LIBELLES"))
                    .montant(Numbers.parseFrenchDouble(cols.get(row, "Montant"), "Montant")));
        }
        return items;
    }
}
