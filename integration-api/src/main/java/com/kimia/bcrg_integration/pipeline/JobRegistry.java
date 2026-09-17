package com.kimia.bcrg_integration.pipeline;

import java.util.Set;

/**
 * Registre des feuilles d'<strong>un</strong> fichier réglementaire (SIGIMF, SITU, FINS, IGEC,
 * PRUD, BALG) : il relie chaque feuille du classeur à son {@link SheetJob}, c'est-à-dire à la
 * chaîne lecture → mapping → assemblage → envoi qui lui correspond.
 * <p>Un registre <em>par fichier</em>, parce que chaque fichier a ses propres feuilles, ses propres
 * DTO et sa propre famille d'endpoints ({@code /api/sigimf/…} pour SIGIMF, {@code /api/imf/…} pour
 * les cinq autres). {@link JobRegistries} choisit le bon d'après le code fichier de la demande, ce
 * qui laisse le pipeline (et l'endpoint interne) identiques quel que soit le fichier traité.
 */
public interface JobRegistry {

    /** Code du fichier réglementaire couvert (ex. « SIGIMF », « SITU ») — clé de résolution et d'audit. */
    String codeFichier();

    /** Le job d'intégration d'une feuille, ou {@code null} si elle n'est pas prise en charge. */
    SheetJob<?, ?> forSheet(String sheetName);

    /** Noms des feuilles prises en charge, dans l'ordre du fichier. */
    Set<String> sheets();

    /**
     * Rythme de transmission d'une feuille. Par défaut {@link Periodicite#MENSUELLE} — le rythme le
     * plus permissif : une feuille qui ne déclare pas son rythme est envoyée, et c'est SIRYF qui
     * tranche. Mieux vaut un refus documenté qu'un envoi retenu sur une supposition.
     */
    default Periodicite periodicite(String sheetName) {
        return Periodicite.MENSUELLE;
    }
}
