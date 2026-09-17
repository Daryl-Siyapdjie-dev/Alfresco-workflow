package com.kimia.bcrg_integration.pipeline;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;

/**
 * Rythme auquel une feuille se transmet. SIRYF refuse une {@code dateArrete} qui ne tombe pas sur la
 * fin de la période attendue, avec un message explicite — « La date fournie n'est pas la fin du
 * trimestre / du semestre / de l'année ».
 *
 * <p><strong>Pourquoi c'est ici et non chez l'appelant.</strong> Dans le workflow documentaire
 * (dépôt → contrôle → validation), personne ne saisit de période : le module la déduit du classeur.
 * Un dossier arrêté au 31 mars part alors en bloc, et ses feuilles semestrielles ou annuelles se
 * font refuser une à une par la BCRG. Savoir <em>avant d'appeler</em> qu'une feuille n'est pas dans
 * son rythme évite un aller-retour réseau, un rejeu inutile (un 5xx est rejoué quatre fois) et un
 * compte rendu illisible.
 *
 * <p><strong>Le défaut est {@link #MENSUELLE}</strong>, le rythme le plus permissif : une feuille
 * dont le rythme n'est pas déclaré est envoyée, et c'est SIRYF qui tranche. Mieux vaut un refus
 * documenté qu'un envoi retenu sur une supposition. Le rythme réel de chaque feuille est déclaré par
 * son {@link JobRegistry} ; l'inventaire de ces rythmes et leur provenance figurent dans
 * {@code BILAN.md}.
 */
public enum Periodicite {

    /** Tous les mois : {@code dateArrete} = dernier jour du mois. */
    MENSUELLE(1),

    /** Fins de trimestre : 31/03, 30/06, 30/09, 31/12. */
    TRIMESTRIELLE(3),

    /** Fins de semestre : 30/06 et 31/12. */
    SEMESTRIELLE(6),

    /** Fin d'année seulement : 31/12. */
    ANNUELLE(12);

    private final int moisParPeriode;

    Periodicite(int moisParPeriode) {
        this.moisParPeriode = moisParPeriode;
    }

    /**
     * Vrai si {@code dateArrete} est une fin de période acceptable pour ce rythme.
     * <p>Toute date valide est d'abord une <strong>fin de mois</strong> : c'est la règle générale de
     * SIRYF, dont les autres rythmes sont des restrictions.
     */
    public boolean accepte(LocalDate dateArrete) {
        if (dateArrete == null || !dateArrete.equals(finDeMois(dateArrete))) {
            return false;
        }
        return dateArrete.getMonthValue() % moisParPeriode == 0;
    }

    /** Fin de la période de ce rythme qui contient {@code date} — la prochaine date acceptable. */
    public LocalDate finDePeriodeContenant(LocalDate date) {
        int mois = date.getMonthValue();
        int finDePeriode = ((mois - 1) / moisParPeriode + 1) * moisParPeriode;
        return finDeMois(LocalDate.of(date.getYear(), Month.of(finDePeriode), 1));
    }

    /**
     * Nom du rythme tel qu'il apparaît dans le message de refus de SIRYF, pour que les deux se
     * répondent — élision comprise (« fin d'année », et non « fin de année »).
     */
    public String periodeLisible() {
        return switch (this) {
            case MENSUELLE -> "du mois";
            case TRIMESTRIELLE -> "du trimestre";
            case SEMESTRIELLE -> "du semestre";
            case ANNUELLE -> "d'année";
        };
    }

    private static LocalDate finDeMois(LocalDate date) {
        return YearMonth.from(date).atEndOfMonth();
    }
}
