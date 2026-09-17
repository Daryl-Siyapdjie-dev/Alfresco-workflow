package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfDebiteurImportant;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.VIII.DIX.DEB} (dix plus importants débiteurs) →
 * {@link SigImfDebiteurImportant} (endpoint {@code /api/sigimf/m8dixdeb},
 * DataModel {@code DataModelImfSigImfDebiteurImportantString}). Feuille-liste (en-tête « N°. »).
 * <p>Colonnes : {@code N°.→numero}, {@code Nom et prénoms→nomEtPrenoms},
 * {@code Date originale→dateOriginale}, {@code Montant original→montantOriginal}, {@code Solde→solde},
 * {@code Retard en capital→retardEnCapital}, {@code Provision comptabilisée→provisionComptabilisee},
 * {@code Garantie→garantie}.
 * <p>⚠️ Écarts : la colonne {@code ENCOURS CREDIT GF} n'a pas de champ cible évident et le champ DTO
 * {@code ratioLimitationEngagement} n'a pas de colonne source → non mappés (à cadrer dans la matrice).
 */
@Component
public class SigImfDebiteurImportantMapper implements SheetMapper<SigImfDebiteurImportant> {

    static final String HEADER_MARKER = "N°.";

    @Override
    public List<SigImfDebiteurImportant> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et prénoms", "Date originale", "Montant original",
                "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");
        List<SigImfDebiteurImportant> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »), pas un enregistrement
            String nom = cols.get(row, "Nom et prénoms");
            if (nom.isEmpty()) continue;
            items.add(new SigImfDebiteurImportant()
                    .numero(numero)
                    .nomEtPrenoms(nom)
                    .dateOriginale(Dates.parseDate(cols.get(row, "Date originale"), "Date originale"))
                    .montantOriginal(Numbers.parseFrenchDouble(cols.get(row, "Montant original"), "Montant original"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .retardEnCapital(Numbers.parseFrenchDouble(cols.get(row, "Retard en capital"), "Retard en capital"))
                    .provisionComptabilisee(
                            Numbers.parseFrenchDouble(cols.get(row, "Provision comptabilisée"), "Provision comptabilisée"))
                    .garantie(emptyToNull(cols.get(row, "Garantie"))));
        }
        return items;
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
