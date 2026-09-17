package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.BilanHorsBilanImf;
import com.kimia.bcrg_integration.excel.SheetTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper <strong>mutualisé</strong> des feuilles {@code FINS_03} (ventilation sectorielle de
 * l'encours des crédits) et {@code FINS_04} (ventilation sectorielle des crédits en souffrance) →
 * items {@link BilanHorsBilanImf} (endpoints {@code /api/imf/fins/fins03} et {@code …/fins04}).
 * Même disposition au détail près, même DTO : un seul mapper, enregistré deux fois.
 * <p>Colonnes : « BILAN » et « HORS BILAN » chapeautent chacun un couple {@code GNF} / {@code %%} ;
 * le lecteur rend les homonymes uniques (« GNF », « GNF (2) », « %% », « %% (2) »).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune à tous les fichiers :
 * elle écarte les chapeaux d'en-tête que la lecture large ramène et la ligne qui numérote les
 * colonnes (« 1 | 2 | 5 = (1+3) »).
 * <p><strong>Lue par position</strong> : la colonne du pourcentage des totaux n'a d'intitulé que sur
 * la ligne de numérotation des colonnes, qui ne fait pas partie de l'en-tête. Le lecteur la nomme
 * donc {@code col42}. Précédent : le bloc géographique de {@code M.XV.RECAP}.
 * <p>Les cellules {@code #DIV/0!} d'un template non rempli arrivent vides : le lecteur rend les
 * formules en erreur comme des cases non remplies.
 */
public class FinsVentilationSectorielleMapper implements SheetMapper<BilanHorsBilanImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "LIBELLES";
    static final String GNF_BILAN = "GNF";
    static final String PCT_BILAN = "%";
    static final String GNF_HORS_BILAN = "GNF (2)";
    static final String PCT_HORS_BILAN = "% (2)";
    static final String TOTAL = "TOTAUX";
    static final String PCT_TOTAL = "col42";

    @Override
    public List<BilanHorsBilanImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, GNF_BILAN, GNF_HORS_BILAN, TOTAL);
        List<BilanHorsBilanImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new BilanHorsBilanImf()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .gnfBilan(Numbers.parseMontantOuRenvoi(cols.get(row, GNF_BILAN), GNF_BILAN))
                    .pourcentageBilan(Numbers.parseMontantOuRenvoi(cols.get(row, PCT_BILAN), PCT_BILAN))
                    .gnfHorsBilan(Numbers.parseMontantOuRenvoi(cols.get(row, GNF_HORS_BILAN), GNF_HORS_BILAN))
                    .pourcentageHorsBilan(Numbers.parseMontantOuRenvoi(cols.get(row, PCT_HORS_BILAN), PCT_HORS_BILAN))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL))
                    .pourcentageTotal(Numbers.parseMontantOuRenvoi(cols.get(row, PCT_TOTAL), PCT_TOTAL)));
        }
        return items;
    }
}
