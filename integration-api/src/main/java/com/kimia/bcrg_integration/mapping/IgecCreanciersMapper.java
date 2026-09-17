package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.DixCreanciers;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_08} (dix principaux créanciers) → items {@link DixCreanciers}
 * (endpoint {@code /api/imf/igec/igec08}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecCreanciersMapper implements SheetMapper<DixCreanciers> {

    static final String CODE = "N°CODE";
    static final String DENOMINATION = "Dénomination complète";
    static final String NATURE = "Nature ressource";
    static final String DATE_OPERATION = "Date de l'opération";
    static final String DATE_ECHEANCE = "Date échéance";
    static final String NOM = "Prénoms et NOM";
    static final String MONNAIE = "Monnaie";
    static final String INITIALE = "Initiale";
    static final String SOLDE = "Solde";

    @Override
    public List<DixCreanciers> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), DENOMINATION, NATURE, INITIALE, SOLDE);
        List<DixCreanciers> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new DixCreanciers()
                    .code(code)
                    .denominationComplete(cols.get(row, DENOMINATION))
                    .natureRessource(cols.get(row, NATURE))
                    .dateOperation(Dates.parseDate(cols.get(row, DATE_OPERATION), DATE_OPERATION))
                    .dateEcheance(Dates.parseDate(cols.get(row, DATE_ECHEANCE), DATE_ECHEANCE))
                    .nomPrenom(cols.get(row, NOM))
                    .monnaie(cols.get(row, MONNAIE))
                    .montantInitial(Numbers.parseMontantOuRenvoi(cols.get(row, INITIALE), INITIALE))
                    .montantSolde(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE), SOLDE)));
        }
        return items;
    }
}
