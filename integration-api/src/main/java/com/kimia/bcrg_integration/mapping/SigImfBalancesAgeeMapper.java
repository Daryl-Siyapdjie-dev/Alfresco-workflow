package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfBalancesAgee;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille SIGIMF {@code M.XXI.BALANCES AGEES} → items {@link SigImfBalancesAgee}
 * (endpoint {@code /api/sigimf/m21balancesagees}).
 * <p>La feuille empile <strong>quatre blocs de même forme</strong> (A : dettes auprès du secteur
 * financier, B : portefeuille de crédits sains, C : dépôts à terme, D : dépôts de garantie), chacun
 * précédé de son titre et de sa propre ligne d'en-tête. Lue avec {@code skipBlankRows}, la feuille
 * ne forme qu'un seul tableau : les en-têtes répétés sont ignorés et les lignes des quatre blocs se
 * suivent, distinguées par leur {@code CODE} ({@code M.XXI.BAL.1} à {@code .36}).
 * <p><strong>Correspondance par position</strong>, à la différence des autres mappers : d'un bloc à
 * l'autre les intitulés changent (« Nombre de dettes » / « Nombres de Crédits » / « Nombres de DAT » /
 * « Nombres de Dépôt de Garantie Financière », « Capital restant dû » / « Montant en cours »…) alors
 * que la structure, elle, est identique. Les colonnes sont donc lues dans l'ordre :
 * {@code CODE, ECHEANCES, nombre, %, montant originel, capital restant/montant en cours, %}.
 * <p>Champ DTO sans source : {@code numeroLigne}. L'appartenance d'une ligne à son bloc n'a pas de
 * champ cible — elle reste portée par le {@code code}.
 */
@Component
public class SigImfBalancesAgeeMapper implements SheetMapper<SigImfBalancesAgee> {

    /** Nombre de colonnes attendues, dans l'ordre décrit ci-dessus. */
    private static final int COLONNES = 7;

    @Override
    public List<SigImfBalancesAgee> map(SheetTable sheet) {
        List<String> headers = sheet.headers();
        if (headers.size() < COLONNES) {
            throw new MappingException("M.XXI.BALANCES AGEES : " + COLONNES + " colonnes attendues, "
                    + headers.size() + " trouvée(s) (en-têtes : " + headers + ")");
        }
        List<SigImfBalancesAgee> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = at(row, headers, 0);
            if (code.isEmpty()) continue;   // titre de bloc (« A)- DETTES… ») ou ligne vide
            items.add(new SigImfBalancesAgee()
                    .code(code)
                    .echeances(at(row, headers, 1))
                    .nombresDettes(number(row, headers, 2))
                    .nombresPercent(number(row, headers, 3))
                    .montantOriginel(number(row, headers, 4))
                    .capitalRestant(number(row, headers, 5))
                    .capitalPercent(number(row, headers, 6)));
        }
        return items;
    }

    /** Valeur de la {@code index}-ième colonne : la clé de ligne est l'en-tête, ou « colN » s'il est vide. */
    private String at(Map<String, String> row, List<String> headers, int index) {
        String header = headers.get(index);
        String key = header.isEmpty() ? "col" + index : header;
        String value = row.get(key);
        return (value == null) ? "" : value;
    }

    private Double number(Map<String, String> row, List<String> headers, int index) {
        return Numbers.parseFrenchDouble(at(row, headers, index), headers.get(index));
    }
}
