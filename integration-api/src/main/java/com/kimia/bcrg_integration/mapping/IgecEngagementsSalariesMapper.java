package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.EngagementsSalaires;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_15} (engagements envers les salariés) → items
 * {@link EngagementsSalaires} (endpoint {@code /api/imf/igec/igec15}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecEngagementsSalariesMapper implements SheetMapper<EngagementsSalaires> {

    static final String CODE = "N°CODE";
    static final String NOM = "Prénons & Nom";
    static final String FONCTION = "Fonction";
    static final String SOLDE_CREDIT = "Solde crédit/prêt";
    static final String SOLDE_AVANCES = "Solde Avances & Autres comptes débiteurs";
    static final String SIGNATURE = "Engagement par signature";
    static final String TOTAL = "Total des engagements";
    static final String RETARD = "Montants en retard";
    static final String PROVISIONS = "Provisions comptabilisées";

    @Override
    public List<EngagementsSalaires> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NOM, FONCTION, SOLDE_CREDIT, TOTAL);
        List<EngagementsSalaires> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new EngagementsSalaires()
                    .code(code)
                    .prenomsNom(cols.get(row, NOM))
                    .fonction(cols.get(row, FONCTION))
                    .soldeCredit(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE_CREDIT), SOLDE_CREDIT))
                    .soldeAvanceAutresComptesDebiteur(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE_AVANCES), SOLDE_AVANCES))
                    .engagementParSignature(Numbers.parseMontantOuRenvoi(cols.get(row, SIGNATURE), SIGNATURE))
                    .totalEngagement(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL))
                    .montantRetard(Numbers.parseMontantOuRenvoi(cols.get(row, RETARD), RETARD))
                    .provisionsComptabilisees(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISIONS), PROVISIONS)));
        }
        return items;
    }
}
