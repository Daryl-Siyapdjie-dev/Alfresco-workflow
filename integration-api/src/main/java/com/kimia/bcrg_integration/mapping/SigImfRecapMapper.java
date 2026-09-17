package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRecap;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XV.RECAP} → items {@link SigImfRecap}
 * (endpoint {@code /api/sigimf/m15recap}, DataModel {@code DataModelImfSigImfRecapString}).
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code VALEUR→valeur}. Les lignes de
 * <em>section</em> (ex. « INFORMATIONS GENERALES » : colonne CODE vide) sont ignorées.
 * <p><strong>Écart connu</strong> : la feuille contient un <em>second bloc</em> après une ligne vide
 * (« Couverture Géographique » : codes {@code M.XVIII.RECAP.47} à {@code .52}, colonnes Conakry /
 * Basse-Moyenne-Haute Guinée / Guinée Forestière / Total) qui alimenterait les champs {@code conakry},
 * {@code basseGuinee}, … {@code total} du DTO. Le lecteur s'arrête à la première ligne vide → ce bloc
 * n'est pas repris ici. À traiter avec la matrice de correspondance (le regroupement des deux blocs
 * dans un même envoi reste à confirmer côté BCRG).
 * <p>Champs DTO sans source dans la feuille : {@code numeroLigne}, {@code section} (énumération A…F).
 */
@Component
public class SigImfRecapMapper implements SheetMapper<SigImfRecap> {

    @Override
    public List<SigImfRecap> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), "CODE", "ELEMENT", "VALEUR");
        List<SigImfRecap> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            String element = cols.get(row, "ELEMENT");
            if (code.isEmpty() || element.isEmpty()) continue;   // ligne de section ou vide
            items.add(new SigImfRecap()
                    .code(code)
                    .element(element)
                    .valeur(Numbers.parseFrenchDouble(cols.get(row, "VALEUR"), "VALEUR")));
        }
        return items;
    }
}
