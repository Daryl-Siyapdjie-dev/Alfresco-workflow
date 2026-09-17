package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.DeposantsPrincipaux;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_03} (principaux déposants) → items
 * {@link DeposantsPrincipaux} (endpoint {@code /api/imf/igec/igec03}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecDeposantsMapper implements SheetMapper<DeposantsPrincipaux> {

    static final String CODE = "N°CODE";
    static final String DEPOSANT = "Nom du déposant (Etablissements de crédit et assimilés ou clientèle)";
    static final String COMPTE_ORDINAIRE = "Compte ordinaire";
    static final String DEPOTS_TERMES = "Dépôts à termes reçus";
    static final String AUTRES_DEPOTS = "Autres dépôts de la clientèle";

    @Override
    public List<DeposantsPrincipaux> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DEPOSANT, COMPTE_ORDINAIRE, DEPOTS_TERMES, AUTRES_DEPOTS);
        List<DeposantsPrincipaux> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new DeposantsPrincipaux()
                    .code(code)
                    .nomDeposant(cols.get(row, DEPOSANT))
                    .compteOrdinaire(Numbers.parseMontantOuRenvoi(cols.get(row, COMPTE_ORDINAIRE), COMPTE_ORDINAIRE))
                    .depotsAtermes(Numbers.parseMontantOuRenvoi(cols.get(row, DEPOTS_TERMES), DEPOTS_TERMES))
                    .depotsClientele(Numbers.parseMontantOuRenvoi(cols.get(row, AUTRES_DEPOTS), AUTRES_DEPOTS)));
        }
        return items;
    }
}
