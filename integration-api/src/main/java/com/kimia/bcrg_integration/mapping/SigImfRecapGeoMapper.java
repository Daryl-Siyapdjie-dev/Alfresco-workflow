package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRecap;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>second bloc</strong> de la feuille SIGIMF {@code M.XV.RECAP} : le tableau
 * « Couverture Géographique » (codes {@code M.XVIII.RECAP.47} à {@code .52}), qui suit le
 * récapitulatif après une ligne vide et dont l'en-tête commence en 2e colonne.
 * <p>Il produit des items {@link SigImfRecap} — le même DTO que le bloc principal, dont il remplit
 * les <em>autres</em> champs : {@code couvertureGeographique} (le libellé de la ligne),
 * {@code conakry}, {@code basseGuinee}, {@code moyenneGuinee}, {@code hauteGuinee},
 * {@code guineeForestiere} et {@code total}. Les deux blocs alimentent donc un seul envoi vers
 * {@code /api/sigimf/m15recap} (voir {@code SheetBlock}).
 * <p>La colonne des codes n'a pas d'intitulé dans la feuille : elle est lue <strong>par position</strong>
 * (1re colonne), les colonnes régionales l'étant par nom.
 * <p>Champs DTO laissés vides ici (ils relèvent du bloc principal) : {@code element}, {@code valeur} ;
 * sans source : {@code numeroLigne}, {@code section}.
 */
@Component
public class SigImfRecapGeoMapper implements SheetMapper<SigImfRecap> {

    /** Marqueur d'en-tête du bloc, en 2e colonne de la feuille. */
    public static final String HEADER_MARKER = "Couverture Géographique";

    private static final String BASSE = "Basse Guinée";
    private static final String MOYENNE = "Moyenne Guinée";
    private static final String HAUTE = "Haute Guinée";
    private static final String FORESTIERE = "Guinée Forestière";

    @Override
    public List<SigImfRecap> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                HEADER_MARKER, "Conakry", BASSE, MOYENNE, HAUTE, FORESTIERE, "Total");
        List<SigImfRecap> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = firstColumn(row, sheet.headers());
            if (code.isEmpty()) continue;   // ligne sans code = non significative
            items.add(new SigImfRecap()
                    .code(code)
                    .couvertureGeographique(cols.get(row, HEADER_MARKER))
                    .conakry(Numbers.parseFrenchDouble(cols.get(row, "Conakry"), "Conakry"))
                    .basseGuinee(Numbers.parseFrenchDouble(cols.get(row, BASSE), BASSE))
                    .moyenneGuinee(Numbers.parseFrenchDouble(cols.get(row, MOYENNE), MOYENNE))
                    .hauteGuinee(Numbers.parseFrenchDouble(cols.get(row, HAUTE), HAUTE))
                    .guineeForestiere(Numbers.parseFrenchDouble(cols.get(row, FORESTIERE), FORESTIERE))
                    .total(Numbers.parseFrenchDouble(cols.get(row, "Total"), "Total")));
        }
        return items;
    }

    /** 1re colonne de la ligne : son en-tête étant vide, le lecteur la nomme « col0 ». */
    private String firstColumn(Map<String, String> row, List<String> headers) {
        String header = headers.isEmpty() ? "" : headers.get(0);
        String value = row.get(header.isEmpty() ? "col0" : header);
        return (value == null) ? "" : value;
    }
}
