package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCreditRestructure;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XIX.CREDITS.RESTRUCTURES} (crédits restructurés) →
 * {@link SigImfCreditRestructure} (endpoint {@code /api/sigimf/m19creditsrestructures},
 * DataModel {@code DataModelImfSigImfCreditRestructureString}). Feuille-liste (en-tête « N°. »).
 * <p>Colonnes mappées (correspondances sûres) : {@code N°.→numero},
 * {@code Nom et prénoms→nomEtPrenoms}, {@code Montant original→montantOriginal}, {@code Solde→solde},
 * {@code Retard en capital→retardCapital}, {@code Garantie→garantie}.
 * <p>⚠️ <strong>Fort écart structure/DTO — mapping partiel, à compléter via la matrice.</strong>
 * Colonnes source SANS champ cible : {@code Volume de financement des crédits}, {@code Quartier},
 * {@code Répartition … par secteur d'activités}, {@code Ville}, {@code Prêts à moyen et long terme},
 * {@code Commune}. Champs DTO SANS colonne source : {@code dateOriginale}, {@code dateRestructuration},
 * {@code montantRestructure}, {@code nombreJoursRetard}, {@code pourcentageCouvert},
 * {@code provisionComptabilisee}. De plus {@code garantie} est un nombre côté DTO (mismatch possible).
 */
@Component
public class SigImfCreditRestructureMapper implements SheetMapper<SigImfCreditRestructure> {

    static final String HEADER_MARKER = "N°.";

    @Override
    public List<SigImfCreditRestructure> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(),
                "N°.", "Nom et prénoms", "Montant original", "Solde", "Retard en capital", "Garantie");
        List<SigImfCreditRestructure> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            Integer numero = Numbers.parseEntierOuNull(cols.get(row, "N°."));
            if (numero == null) continue;   // ligne de total (« TOTAL | N/A | N/A… »), pas un enregistrement
            String nom = cols.get(row, "Nom et prénoms");
            if (nom.isEmpty()) continue;
            items.add(new SigImfCreditRestructure()
                    .numero(numero)
                    .nomEtPrenoms(nom)
                    .montantOriginal(Numbers.parseFrenchDouble(cols.get(row, "Montant original"), "Montant original"))
                    .solde(Numbers.parseFrenchDouble(cols.get(row, "Solde"), "Solde"))
                    .retardCapital(Numbers.parseFrenchDouble(cols.get(row, "Retard en capital"), "Retard en capital"))
                    .garantie(Numbers.parseFrenchDouble(cols.get(row, "Garantie"), "Garantie")));
        }
        return items;
    }
}
