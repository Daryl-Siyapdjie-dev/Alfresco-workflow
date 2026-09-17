package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSCreditADirigeant;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.VI.ADM.DIR} → items {@link SigImfSCreditADirigeant}
 * (endpoint {@code /api/sigimf/m6admdir}, DataModel {@code DataModelImfSigImfSCreditADirigeantString}).
 * <p>Feuille de type <em>liste</em> (crédits aux dirigeants/administrateurs) : l'en-tête n'est pas
 * « CODE » mais « N°. » → lecture via {@code readSheet(wb, "M.VI.ADM.DIR", "N°.")}.
 * <p>Colonnes : {@code N°.→numero}, {@code Nom et prénoms→nomEtPrenoms}, {@code Fonction→fonction},
 * {@code Date d'octroi→dateOctroi} (jj/mm/aaaa), {@code Montant initial→montantInitial},
 * {@code Solde→solde}, {@code Retard en capital→retardEnCapital},
 * {@code Provision comptabilisée→provisionComptabilisee}, {@code Garantie→garantie}.
 * Les lignes sans bénéficiaire (colonne « Nom et prénoms » vide) sont ignorées.
 */
@Component
public class SigImfCreditDirigeantMapper implements SheetMapper<SigImfSCreditADirigeant> {

    static final String HEADER_MARKER = "N°.";

    @Override
    public List<SigImfSCreditADirigeant> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et prénoms", "Fonction", "Date d'octroi", "Montant initial",
                "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");
        List<SigImfSCreditADirigeant> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »), pas un enregistrement
            String nom = cols.get(row, "Nom et prénoms");
            if (nom.isEmpty()) continue;   // ligne sans bénéficiaire = placeholder
            items.add(new SigImfSCreditADirigeant()
                    .numero(numero)
                    .nomEtPrenoms(nom)
                    .fonction(cols.get(row, "Fonction"))
                    .dateOctroi(Dates.parseDate(cols.get(row, "Date d'octroi"), "Date d'octroi"))
                    .montantInitial(Numbers.parseFrenchDouble(cols.get(row, "Montant initial"), "Montant initial"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .retardEnCapital(Numbers.parseFrenchDouble(cols.get(row, "Retard en capital"), "Retard en capital"))
                    .provisionComptabilisee(
                            Numbers.parseFrenchDouble(cols.get(row, "Provision comptabilisée"), "Provision comptabilisée"))
                    .garantie(cols.get(row, "Garantie")));
        }
        return items;
    }
}
