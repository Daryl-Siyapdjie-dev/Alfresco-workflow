package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfInformationGenerale;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.0.INFO.G} → items {@link SigImfInformationGenerale}
 * (endpoint {@code /api/sigimf/m0infog}).
 * <p>Correspondance 1:1 avec les colonnes du template rempli : {@code CODE → code},
 * {@code INFORMATION → information}, {@code DONNEES → donnee}. Sert de référence pour les autres
 * feuilles. Si la source Alfresco s'avère différente du template, seule cette classe évolue.
 */
@Component
public class SigImfInfoGeneraleMapper implements SheetMapper<SigImfInformationGenerale> {

    @Override
    public List<SigImfInformationGenerale> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", "INFORMATION", "DONNEES");
        List<SigImfInformationGenerale> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;   // ligne sans code = non significative
            items.add(new SigImfInformationGenerale()
                    .code(code)
                    .information(cols.get(row, "INFORMATION"))
                    .donnee(cols.get(row, "DONNEES")));
        }
        return items;
    }
}
