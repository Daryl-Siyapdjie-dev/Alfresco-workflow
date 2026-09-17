package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCreancierPlusImportant;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XVIII.DIX.CREANCIERS} (dix plus importants créanciers) →
 * {@link SigImfCreancierPlusImportant} (endpoint {@code /api/sigimf/m18dixcreanciers},
 * DataModel {@code DataModelImfSigImfCreancierPlusImportantString}). Feuille-liste (en-tête « N°. »).
 * <p>Colonnes mappées : {@code N°.→numero}, {@code Nom et prénoms→nomEtPrenoms},
 * {@code Date originale→dateOriginale}, {@code Montant original→montantOriginal}, {@code Solde→solde},
 * {@code Entreprise individuelle ou personnes morales→natureDeRessource}.
 * <p>⚠️ Écarts (analyse à confirmer avec la matrice) : la colonne {@code Nombre d'employé} n'a pas de
 * champ cible ; le champ DTO {@code dateEcheance} n'a pas de colonne source → non mappés.
 */
@Component
public class SigImfCreancierImportantMapper implements SheetMapper<SigImfCreancierPlusImportant> {

    static final String HEADER_MARKER = "N°.";

    private static final String COL_NATURE = "Nature de Ressource";
    private static final String COL_ECHEANCE = "Date d'échéance";

    @Override
    public List<SigImfCreancierPlusImportant> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et prénoms", "Date originale", "Montant original", "Solde",
                COL_NATURE, COL_ECHEANCE);
        List<SigImfCreancierPlusImportant> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »)
            String nom = cols.get(row, "Nom et prénoms");
            if (nom.isEmpty()) continue;   // ligne sans bénéficiaire = placeholder (cohérent avec les autres listes)
            items.add(new SigImfCreancierPlusImportant()
                    .numero(numero)
                    .nomEtPrenoms(emptyToNull(nom))
                    .dateOriginale(Dates.parseDate(cols.get(row, "Date originale"), "Date originale"))
                    .montantOriginal(Numbers.parseFrenchDouble(cols.get(row, "Montant original"), "Montant original"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .natureDeRessource(emptyToNull(cols.get(row, COL_NATURE)))
                    .dateEcheance(Dates.parseDate(cols.get(row, COL_ECHEANCE), COL_ECHEANCE)));
        }
        return items;
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
