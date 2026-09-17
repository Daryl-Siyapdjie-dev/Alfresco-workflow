package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ImmobiisationAmmortissementImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code FINS_15} (immobilisations et amortissements) → items
 * {@link ImmobiisationAmmortissementImf} (endpoint {@code /api/imf/fins/fins15}).
 * <p>Deux blocs de colonnes — mouvements de la période, puis amortissements — chacun clos par un
 * « TOTAL ». Le lecteur rend les homonymes uniques : « TOTAL » (mouvements) et « TOTAL (2) »
 * (amortissements).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune à tous les fichiers :
 * elle écarte les chapeaux d'en-tête que la lecture large ramène et la ligne qui numérote les
 * colonnes (« 1 | 2 | 5 = (1+3) »).
 */
@Component
public class FinsImmobilisationsMapper implements SheetMapper<ImmobiisationAmmortissementImf> {

    static final String CODE = "N°CODE";
    static final String LIBELLE = "LIBELLES";
    static final String VALEUR_PRECEDENTE = "VALEUR A LA FIN DE LA PERIODE PRECEDENTE";
    static final String ACQUISITIONS = "Acquisitions / Ajustements";
    static final String APPORTS_TIERS = "Immobilist. Apportées par tiers";
    static final String SORTIES = "Immobilist. sorties de l'actif";
    static final String TOTAL_MOUVEMENTS = "TOTAL";
    static final String VALEURS_BRUTES = "VALEURS BRUTES DE FIN DE PERIODE";
    static final String CUMUL_PRECEDENT = "Cumul Période Précédente";
    static final String DOTATION = "Dotation de la Période/Ajustements";
    static final String AMT_SORTIES = "Amt Immob sorties de l'actif";
    static final String TOTAL_AMORTISSEMENTS = "TOTAL (2)";
    static final String VALEUR_NETTE = "VALEURS NETTES COMPTABLE";

    @Override
    public List<ImmobiisationAmmortissementImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, VALEUR_PRECEDENTE, TOTAL_MOUVEMENTS, TOTAL_AMORTISSEMENTS);
        List<ImmobiisationAmmortissementImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ImmobiisationAmmortissementImf()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .valeurFinPeriodePrecedente(Numbers.parseMontantOuRenvoi(cols.get(row, VALEUR_PRECEDENTE), VALEUR_PRECEDENTE))
                    .acquisitionsAjustements(Numbers.parseMontantOuRenvoi(cols.get(row, ACQUISITIONS), ACQUISITIONS))
                    .immobilistApporteParTiers(Numbers.parseMontantOuRenvoi(cols.get(row, APPORTS_TIERS), APPORTS_TIERS))
                    .immoSortiesActif(Numbers.parseMontantOuRenvoi(cols.get(row, SORTIES), SORTIES))
                    .totalMouvementPeriode(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL_MOUVEMENTS), TOTAL_MOUVEMENTS))
                    .valeurBrutesFinPeriode(Numbers.parseMontantOuRenvoi(cols.get(row, VALEURS_BRUTES), VALEURS_BRUTES))
                    .cumulPeriodePrecedente(Numbers.parseMontantOuRenvoi(cols.get(row, CUMUL_PRECEDENT), CUMUL_PRECEDENT))
                    .dotationPeriodeAjustement(Numbers.parseMontantOuRenvoi(cols.get(row, DOTATION), DOTATION))
                    .amtImmobSortieActif(Numbers.parseMontantOuRenvoi(cols.get(row, AMT_SORTIES), AMT_SORTIES))
                    .totalAmortissement(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL_AMORTISSEMENTS), TOTAL_AMORTISSEMENTS))
                    .valeurNetteComptable(Numbers.parseMontantOuRenvoi(cols.get(row, VALEUR_NETTE), VALEUR_NETTE)));
        }
        return items;
    }
}
