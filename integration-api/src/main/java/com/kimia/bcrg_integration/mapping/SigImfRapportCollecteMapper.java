package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfRapportCollecte;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper mutualisé des feuilles SIGIMF partageant le DTO {@link SigImfRapportCollecte} :
 * <ul>
 *   <li>{@code M.XIII.OATA} → {@code /api/sigimf/m13oata}</li>
 *   <li>{@code M.XIV.FS} → {@code /api/sigimf/m14fs}</li>
 *   <li>{@code M.XVI.CERS} → {@code /api/sigimf/m16cers}</li>
 * </ul>
 * DataModel commun {@code DataModelImfSigImfRapportCollecteString}.
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code LIBELLE→libelle},
 * {@code MONTANT GNF→montantGnf}, et {@code N° Compte→nCompte} <em>optionnelle</em> (présente sur
 * OATA/CERS, absente sur FS → {@code null}). Champs {@code section}/{@code categorie}/{@code numeroLigne}
 * sans colonne source directe → non renseignés (à cadrer dans la matrice).
 */
@Component
public class SigImfRapportCollecteMapper implements SheetMapper<SigImfRapportCollecte> {

    @Override
    public List<SigImfRapportCollecte> map(SheetTable sheet) {
        // N° Compte n'est pas requis : FS ne l'a pas. get() renvoie "" si la colonne est absente.
        Columns cols = Columns.of(sheet.headers(), "CODE", "ELEMENT", "LIBELLE", "MONTANT GNF");
        List<SigImfRapportCollecte> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, "CODE");
            if (code.isEmpty()) continue;
            items.add(new SigImfRapportCollecte()
                    .code(code)
                    .element(cols.get(row, "ELEMENT"))
                    .nCompte(Numbers.parseFrenchDouble(cols.get(row, "N° Compte"), "N° Compte"))
                    .libelle(cols.get(row, "LIBELLE"))
                    .montantGnf(Numbers.parseFrenchDouble(cols.get(row, "MONTANT GNF"), "MONTANT GNF")));
        }
        return items;
    }
}
