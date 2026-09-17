package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ExpositionsPrincipales;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_02} (principales expositions) → items
 * {@link ExpositionsPrincipales} (endpoint {@code /api/imf/igec/igec02}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecExpositionsMapper implements SheetMapper<ExpositionsPrincipales> {

    static final String CODE = "N°CODE";
    static final String CONTREPARTIE = "Nom de la contrepartie (Etablissements de crédit et assimilés ou clientèle)";
    static final String BILAN = "Expositions inscrites au bilan";
    static final String HORS_BILAN = "Expositions inscrites au hors bilan";
    static final String PROVISIONS = "Provisions Constituées";
    static final String NETTES = "Expositions nettes";

    @Override
    public List<ExpositionsPrincipales> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), CONTREPARTIE, BILAN, HORS_BILAN, PROVISIONS, NETTES);
        List<ExpositionsPrincipales> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ExpositionsPrincipales()
                    .code(code)
                    .nomContrepartie(cols.get(row, CONTREPARTIE))
                    .expositionBilan(Numbers.parseMontantOuRenvoi(cols.get(row, BILAN), BILAN))
                    .expositionsHorsBilan(Numbers.parseMontantOuRenvoi(cols.get(row, HORS_BILAN), HORS_BILAN))
                    .provisionsConstituees(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISIONS), PROVISIONS))
                    .expositionsNettes(Numbers.parseMontantOuRenvoi(cols.get(row, NETTES), NETTES)));
        }
        return items;
    }
}
