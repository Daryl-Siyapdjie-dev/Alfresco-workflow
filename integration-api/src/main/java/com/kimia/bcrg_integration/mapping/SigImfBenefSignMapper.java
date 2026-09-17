package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBenefSign;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XX.DIX.BENEF.SIGN} (dix plus importants bénéficiaires de
 * signatures) → {@link SigImfBenefSign} (endpoint {@code /api/sigimf/m20benefsign},
 * DataModel {@code DataModelImfSigImfBenefSignString}). Feuille-liste (en-tête « N°. »).
 * <p>Colonnes : {@code N°.→numero}, {@code Nom et prénoms→nomEtPrenoms},
 * {@code Date originale→dateOriginale}, {@code Montant original→montantOriginal}, {@code Solde→solde},
 * {@code Retard en capital→retardCapital}, {@code Provision comptabilisée→provisionComptabilisee},
 * {@code Garantie→garantie}.
 * <p>⚠️ Dans ce DTO {@code garantie} est un <strong>nombre</strong> (Double) — si la colonne
 * « Garantie » contient du texte dans un fichier réel, une {@link MappingException} sera levée
 * (mismatch à confirmer avec la matrice de correspondance).
 */
@Component
public class SigImfBenefSignMapper implements SheetMapper<SigImfBenefSign> {

    static final String HEADER_MARKER = "N°.";

    @Override
    public List<SigImfBenefSign> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et prénoms", "Date originale", "Montant original",
                "Solde", "Retard en capital", "Provision comptabilisée", "Garantie");
        List<SigImfBenefSign> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »), pas un enregistrement
            String nom = cols.get(row, "Nom et prénoms");
            if (nom.isEmpty()) continue;
            items.add(new SigImfBenefSign()
                    .numero(numero)
                    .nomEtPrenoms(nom)
                    .dateOriginale(Dates.parseDate(cols.get(row, "Date originale"), "Date originale"))
                    .montantOriginal(Numbers.parseFrenchDouble(cols.get(row, "Montant original"), "Montant original"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .retardCapital(Numbers.parseFrenchDouble(cols.get(row, "Retard en capital"), "Retard en capital"))
                    .provisionComptabilisee(
                            Numbers.parseFrenchDouble(cols.get(row, "Provision comptabilisée"), "Provision comptabilisée"))
                    .garantie(Numbers.parseFrenchDouble(cols.get(row, "Garantie"), "Garantie")));
        }
        return items;
    }
}
