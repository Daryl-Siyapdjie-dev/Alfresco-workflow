package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfSituationImpayeCredit;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper du <strong>second tableau</strong> de {@code M.IV.IMPAYE} — les ratios de portefeuille à
 * risque (PAR 1, PAR 30, PAR 90) — vers les mêmes items {@link SigImfSituationImpayeCredit} que le
 * tableau des impayés, dans le même envoi.
 *
 * <p>Contrairement à {@code M.I.BILAN} et {@code M.II.RESULTAT}, ce second tableau n'a pas les mêmes
 * colonnes que le premier : il n'en garde que trois ({@code Capital restant dû}, {@code %}) et en
 * ajoute une, {@code Norme}, qui porte le seuil réglementaire à respecter. D'où un mapper distinct
 * plutôt qu'un paramètre du premier.
 *
 * <p>Colonnes : {@code CODE→code}, {@code ELEMENT→element}, {@code Capital restant dû→capitalRestant},
 * {@code %→capitalRestantPercent}, {@code Norme→norme}. Le champ {@code section} (énumération A–F
 * contrainte, sémantique non documentée) reste non renseigné, comme le {@code typeColumn} de M.IX.SG.
 */
@Component
public class SigImfImpayeRatiosMapper implements SheetMapper<SigImfSituationImpayeCredit> {

    /** Intitulé qui n'existe que dans ce tableau : il sert aussi à le reconnaître. */
    public static final String COL_NORME = "Norme";

    private static final String COL_CODE = "CODE";
    private static final String COL_ELEMENT = "ELEMENT";
    private static final String COL_CAPITAL = "Capital restant dû";
    private static final String COL_PCT = "%";

    @Override
    public List<SigImfSituationImpayeCredit> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), COL_CODE, COL_ELEMENT, COL_CAPITAL, COL_PCT, COL_NORME);
        List<SigImfSituationImpayeCredit> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, COL_CODE);
            if (code.isEmpty()) continue;
            items.add(new SigImfSituationImpayeCredit()
                    .code(code)
                    .element(cols.get(row, COL_ELEMENT))
                    .capitalRestant(Numbers.parseFrenchDouble(cols.get(row, COL_CAPITAL), COL_CAPITAL))
                    .capitalRestantPercent(Numbers.parseFrenchDouble(cols.get(row, COL_PCT), COL_PCT))
                    .norme(Numbers.parseFrenchDouble(cols.get(row, COL_NORME), COL_NORME)));
        }
        return items;
    }
}
