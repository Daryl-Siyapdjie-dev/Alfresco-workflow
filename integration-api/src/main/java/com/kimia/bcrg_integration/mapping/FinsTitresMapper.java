package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.EtablissementCreditImf;
import com.kimia.bcrg_integration.excel.SheetTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper <strong>mutualisé</strong> des feuilles {@code FINS_07_GNF} (titres détenus par les
 * établissements de crédit) et {@code FINS_08_GNF} (titres émis par l'établissement assujetti) →
 * items {@link EtablissementCreditImf} (endpoints {@code …/fins07GNF} et {@code …/fins08GNF}).
 * <p>Même ventilation par type de détenteur (BCRG, CCP, TP, banques, EF, IMF, IF spécialisées, IF
 * non résidentes, total) et même DTO. Seul l'intitulé de la colonne des libellés change
 * (« LIBELLES » / « DETTES ») : il est passé au constructeur.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune à tous les fichiers :
 * elle écarte les chapeaux d'en-tête que la lecture large ramène et la ligne qui numérote les
 * colonnes (« 1 | 2 | 5 = (1+3) »).
 * <p>{@code valeurTotalLigne} n'a pas de colonne source.
 */
public class FinsTitresMapper implements SheetMapper<EtablissementCreditImf> {

    static final String BCRG = "BCRG";
    static final String CCP = "CCP";
    static final String TP = "TP";
    static final String BANQUES = "Banques";
    static final String ETABLISSEMENTS_FINANCIERS = "Etablissements Financiers";
    static final String MICROFINANCE = "Institutions de microfinance";
    static final String SPECIALISEES = "Institutions Financières spécialisées";
    static final String NON_RESIDENTES = "Institutions Financières Non Residentes";
    static final String TOTAL = "TOTAL";

    private final String colonneCode;
    private final String colonneLibelle;

    /**
     * @param colonneCode    intitulé de la colonne des codes : « N°CODE » (FINS_07_GNF) ou
     *                       « CODE » (FINS_08_GNF), les deux feuilles ne l'écrivent pas pareil
     * @param colonneLibelle intitulé de la colonne des libellés : « LIBELLES » ou « DETTES »
     */
    public FinsTitresMapper(String colonneCode, String colonneLibelle) {
        this.colonneCode = colonneCode;
        this.colonneLibelle = colonneLibelle;
    }

    @Override
    public List<EtablissementCreditImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), colonneCode, colonneLibelle, BCRG, TOTAL);
        List<EtablissementCreditImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, colonneCode);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new EtablissementCreditImf()
                    .code(code)
                    .libelle(cols.get(row, colonneLibelle))
                    .bcrg(Numbers.parseMontantOuRenvoi(cols.get(row, BCRG), BCRG))
                    .ccp(Numbers.parseMontantOuRenvoi(cols.get(row, CCP), CCP))
                    .tp(Numbers.parseMontantOuRenvoi(cols.get(row, TP), TP))
                    .banque(Numbers.parseMontantOuRenvoi(cols.get(row, BANQUES), BANQUES))
                    .etablissementsFinanciers(Numbers.parseMontantOuRenvoi(cols.get(row, ETABLISSEMENTS_FINANCIERS), ETABLISSEMENTS_FINANCIERS))
                    .institutionMicrofinance(Numbers.parseMontantOuRenvoi(cols.get(row, MICROFINANCE), MICROFINANCE))
                    .institutionFinanciereSpecialise(Numbers.parseMontantOuRenvoi(cols.get(row, SPECIALISEES), SPECIALISEES))
                    .institutionFinanciereNonResident(Numbers.parseMontantOuRenvoi(cols.get(row, NON_RESIDENTES), NON_RESIDENTES))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
