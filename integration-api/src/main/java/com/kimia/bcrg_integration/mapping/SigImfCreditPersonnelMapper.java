package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSCreditAPersonnel;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.VII.PERSONNEL} → items {@link SigImfSCreditAPersonnel}
 * (endpoint {@code /api/sigimf/m7personnel}, DataModel {@code DataModelImfSigImfSCreditAPersonnelString}).
 * <p>Feuille de type <em>liste</em> (crédits au personnel), en-tête « N°. »
 * → lecture via {@code readSheet(wb, "M.VII.PERSONNEL", "N°.")}.
 * <p>Colonnes : {@code N°.→numero}, {@code Nom et Prénoms→nomEtPrenoms},
 * {@code Date originale→dateOriginale} (jj/mm/aaaa), {@code Montant original→montantOriginal},
 * {@code Solde→solde}, {@code Retard en capital→retardEnCapital},
 * {@code Provision comptabilisée→provisionComptabilisee}, {@code Garantie→garantie}.
 * Les lignes sans bénéficiaire (colonne « Nom et Prénoms » vide) sont ignorées.
 */
@Component
public class SigImfCreditPersonnelMapper implements SheetMapper<SigImfSCreditAPersonnel> {

    static final String HEADER_MARKER = "N°.";

    @Override
    public List<SigImfSCreditAPersonnel> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et Prénoms", "Date originale", "Montant original",
                "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");
        List<SigImfSCreditAPersonnel> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »), pas un enregistrement
            String nom = cols.get(row, "Nom et Prénoms");
            if (nom.isEmpty()) continue;   // ligne sans bénéficiaire = placeholder
            items.add(new SigImfSCreditAPersonnel()
                    .numero(numero)
                    .nomEtPrenoms(nom)
                    .dateOriginale(Dates.parseDate(cols.get(row, "Date originale"), "Date originale"))
                    .montantOriginal(Numbers.parseFrenchDouble(cols.get(row, "Montant original"), "Montant original"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .retardEnCapital(Numbers.parseFrenchDouble(cols.get(row, "Retard en capital"), "Retard en capital"))
                    .provisionComptabilisee(
                            Numbers.parseFrenchDouble(cols.get(row, "Provision comptabilisée"), "Provision comptabilisée"))
                    .garantie(cols.get(row, "Garantie")));
        }
        return items;
    }
}
