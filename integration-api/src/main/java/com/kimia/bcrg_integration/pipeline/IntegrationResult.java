package com.kimia.bcrg_integration.pipeline;

/**
 * Résultat de l'intégration d'une feuille : ce que le module remonte au workflow appelant.
 * <p>Le {@code outcome} classe l'issue pour piloter la suite (cf. §7 du CDC) :
 * <ul>
 *   <li>{@code SUCCES} : envoi accepté (HTTP 2xx) <strong>et</strong> sans erreur de cohérence ;</li>
 *   <li>{@code ACCEPTE_AVEC_ERREURS} : SIRYF a accepté la transmission mais <strong>refusé certaines
 *       lignes</strong> (son corps de réponse porte un tableau {@code erreurs} non vide). La période
 *       est consommée, la donnée est partie <em>incomplète</em> : quelqu'un doit regarder. Sans cette
 *       issue, un envoi « accepté avec douze lignes rejetées » passait pour un succès ;</li>
 *   <li>{@code ERREUR_DONNEES} : lecture/mapping en échec (feuille/colonne manquante, donnée invalide)
 *       → correction métier, <strong>pas</strong> de rejeu automatique ;</li>
 *   <li>{@code ERREUR_API} : l'API a rejeté (4xx = fonctionnel, 5xx = technique/rejouable) ;</li>
 *   <li>{@code ERREUR_TECHNIQUE} : incident inattendu (à investiguer) ;</li>
 *   <li>{@code DEJA_TRANSMIS} : envoi ignoré car déjà réussi pour la même clé (idempotence, §7) ;</li>
 *   <li>{@code HORS_PERIODE} : envoi <strong>non tenté</strong>, la feuille ne se transmet pas à cette
 *       date (une semestrielle dans un dossier arrêté en mars). Ce n'est ni un défaut du classeur ni
 *       un refus de la BCRG — c'est simplement que le tour de cette feuille n'est pas venu.</li>
 * </ul>
 * <p>{@code DEJA_TRANSMIS} et {@code HORS_PERIODE} ne sont donc <strong>pas des erreurs</strong> :
 * un dossier qui n'en contient pas d'autres reste complet.
 *
 * @param feuille    nom de la feuille traitée
 * @param outcome    classe d'issue
 * @param itemCount  nombre d'items produits par le mapping
 * @param httpStatus code HTTP de la réponse (null si aucun appel n'a eu lieu)
 * @param detail     message lisible (corps de réponse ou message d'erreur)
 */
public record IntegrationResult(String feuille, Outcome outcome, int itemCount,
                                Integer httpStatus, String detail) {

    public enum Outcome {
        SUCCES, ACCEPTE_AVEC_ERREURS, ERREUR_DONNEES, ERREUR_API, ERREUR_TECHNIQUE,
        DEJA_TRANSMIS, HORS_PERIODE
    }

    public boolean isSucces() {
        return outcome == Outcome.SUCCES;
    }

    /**
     * true si SIRYF a <strong>reçu et accepté</strong> la transmission — succès complet ou non.
     * <p>Distinct de {@link #isSucces()} : une transmission acceptée avec des lignes refusées a bel
     * et bien consommé sa période. C'est donc ce prédicat, et non le succès, qui gouverne
     * l'idempotence — la rejouer se heurterait à « cette date d'arrêté est déjà utilisée ».
     */
    public boolean estTransmis() {
        return outcome == Outcome.SUCCES || outcome == Outcome.ACCEPTE_AVEC_ERREURS;
    }

    static IntegrationResult dejaTransmis(String feuille) {
        return new IntegrationResult(feuille, Outcome.DEJA_TRANSMIS, 0, null, "déjà transmis (idempotence)");
    }

    /**
     * La feuille ne se transmet pas à cette date : rien n'est envoyé, et le détail dit quelle date
     * conviendrait — de quoi replanifier sans avoir à connaître le calendrier de chaque feuille.
     */
    static IntegrationResult horsPeriode(String feuille, Periodicite periodicite,
                                         java.time.LocalDate demandee) {
        return new IntegrationResult(feuille, Outcome.HORS_PERIODE, 0, null,
                "feuille " + periodicite.name().toLowerCase(java.util.Locale.ROOT)
                        + " : " + demandee + " n'est pas une fin " + periodicite.periodeLisible()
                        + " (prochaine date acceptable : "
                        + periodicite.finDePeriodeContenant(demandee) + ")");
    }

    static IntegrationResult succes(String feuille, int itemCount, int httpStatus, String body) {
        return new IntegrationResult(feuille, Outcome.SUCCES, itemCount, httpStatus, body);
    }

    /** Transmission acceptée, mais des lignes ont été refusées par les contrôles de cohérence. */
    static IntegrationResult accepteAvecErreurs(String feuille, int itemCount, int httpStatus,
                                                String resumeDesErreurs) {
        return new IntegrationResult(feuille, Outcome.ACCEPTE_AVEC_ERREURS, itemCount, httpStatus,
                resumeDesErreurs);
    }

    static IntegrationResult erreurDonnees(String feuille, int itemCount, String detail) {
        return new IntegrationResult(feuille, Outcome.ERREUR_DONNEES, itemCount, null, detail);
    }

    static IntegrationResult erreurApi(String feuille, int itemCount, int httpStatus, String detail) {
        return new IntegrationResult(feuille, Outcome.ERREUR_API, itemCount, httpStatus, detail);
    }

    static IntegrationResult erreurTechnique(String feuille, int itemCount, String detail) {
        return new IntegrationResult(feuille, Outcome.ERREUR_TECHNIQUE, itemCount, null, detail);
    }
}
