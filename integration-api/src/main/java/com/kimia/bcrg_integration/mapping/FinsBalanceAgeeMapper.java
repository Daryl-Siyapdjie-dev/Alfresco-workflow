package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.BalanceAgeePortefeuille;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code FINS_17} (balance âgée du portefeuille des crédits) → items
 * {@link BalanceAgeePortefeuille} (endpoint {@code /api/imf/fins/fins17}).
 * <p>{@code portafeuilleArisque} est un champ <em>texte</em> côté DTO : la tranche de PAR est reprise
 * telle quelle.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune à tous les fichiers :
 * elle écarte les chapeaux d'en-tête que la lecture large ramène et la ligne qui numérote les
 * colonnes (« 1 | 2 | 5 = (1+3) »).
 */
@Component
public class FinsBalanceAgeeMapper implements SheetMapper<BalanceAgeePortefeuille> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String ELEMENT = "ELEMENTS";
    static final String NOMBRE_CREDITS = "NOMBRE DE CREDITS";
    static final String SOLDE = "SOLDE EN GNF";
    static final String PAR = "PORTEFEUILLE A RISQUE (PAR)";
    static final String MONTANT_TRANCHE = "Montant Par tranche du PAR";
    static final String TAUX_PAR = "Taux du PAR en %";

    @Override
    public List<BalanceAgeePortefeuille> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), ELEMENT, NOMBRE_CREDITS, SOLDE);
        List<BalanceAgeePortefeuille> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new BalanceAgeePortefeuille()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .element(cols.get(row, ELEMENT))
                    .nombreCredits(Numbers.parseEntierOuRenvoi(cols.get(row, NOMBRE_CREDITS), NOMBRE_CREDITS))
                    .soldeGNF(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE), SOLDE))
                    .portafeuilleArisque(cols.get(row, PAR))
                    .montantParTranche(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT_TRANCHE), MONTANT_TRANCHE))
                    .tauxPAR(Numbers.parseMontantOuRenvoi(cols.get(row, TAUX_PAR), TAUX_PAR)));
        }
        return items;
    }
}
