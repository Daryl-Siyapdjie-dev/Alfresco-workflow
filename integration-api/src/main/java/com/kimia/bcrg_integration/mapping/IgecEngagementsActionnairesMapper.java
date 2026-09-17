package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.EngagementsActionnairesAdminMandataires;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_16} (engagements envers actionnaires, administrateurs et
 * mandataires) → items {@link EngagementsActionnairesAdminMandataires}
 * (endpoint {@code /api/imf/igec/igec16}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p><strong>Écart assumé</strong> : le DTO distingue {@code engagementParSignature} et
 * {@code soldeAvanceAutresComptesDebiteur}, là où cette feuille les <em>fusionne</em> en une seule
 * colonne (« Engagement par signature & Autres comptes débiteurs ») — contrairement à IGEC_15, qui
 * les sépare. La colonne est versée dans {@code engagementParSignature}, celui des deux qui donne son
 * nom à l'intitulé, et le second champ reste vide : rien ne permet de répartir le montant.
 */
@Component
public class IgecEngagementsActionnairesMapper implements SheetMapper<EngagementsActionnairesAdminMandataires> {

    static final String CODE = "N°CODE";
    static final String NOM = "Prénons & Nom";
    static final String FONCTION = "Fonction";
    static final String SOLDE_CREDIT = "Solde crédit/prêt";
    static final String SIGNATURE = "Engagement par signature & Autres comptes débiteurs";
    static final String TOTAL = "Total des engagements";
    static final String RETARD = "Montants en Retard";
    static final String PROVISION = "Provision comptabilisée";

    @Override
    public List<EngagementsActionnairesAdminMandataires> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NOM, FONCTION, SOLDE_CREDIT, TOTAL);
        List<EngagementsActionnairesAdminMandataires> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new EngagementsActionnairesAdminMandataires()
                    .code(code)
                    .prenomsNom(cols.get(row, NOM))
                    .fonction(cols.get(row, FONCTION))
                    .soldeCredit(Numbers.parseMontantOuRenvoi(cols.get(row, SOLDE_CREDIT), SOLDE_CREDIT))
                    .engagementParSignature(Numbers.parseMontantOuRenvoi(cols.get(row, SIGNATURE), SIGNATURE))
                    .totalEngagement(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL))
                    .montantRetard(Numbers.parseMontantOuRenvoi(cols.get(row, RETARD), RETARD))
                    .provisionsComptabilisees(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISION), PROVISION)));
        }
        return items;
    }
}
