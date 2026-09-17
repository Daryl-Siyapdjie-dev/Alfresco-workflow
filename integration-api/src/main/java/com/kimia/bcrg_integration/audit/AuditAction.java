package com.kimia.bcrg_integration.audit;

/**
 * Étape du parcours d'un dossier, telle qu'elle apparaît dans la piste d'audit (§9, ticket 7.2).
 * <p>Les cinq premières sont émises par le pipeline actuel ; les trois dernières sont réservées à la
 * chaîne de transmission BCRG ({@code verification} → {@code autorisation} → {@code soumission}),
 * câblée dans {@code TransmissionClient} mais pas encore orchestrée.
 */
public enum AuditAction {

    /** Prise en charge d'un classeur : fichier ouvert, feuilles à traiter identifiées. */
    DEPOT,

    /** Lecture + mapping d'une feuille : nombre d'items produits, ou erreur de données. */
    TRANSFORMATION,

    /** Envoi du payload à l'API BCRG et réponse obtenue (l'accusé est archivé à part). */
    IMPORT,

    /** Nouvelle tentative après une erreur technique/transitoire (§7). */
    REPRISE,

    /** Envoi ignoré : la même clé d'idempotence a déjà été transmise avec succès (§7). */
    IDEMPOTENCE,

    /** Fin de traitement d'un dossier : bilan par classe d'issue. */
    CLOTURE,

    /** Réservé : appel {@code /verification} de la chaîne de transmission. */
    VERIFICATION,

    /** Réservé : appel {@code /transmissionImf/autorisation}. */
    AUTORISATION,

    /** Réservé : appel {@code /soumissionImf}. */
    SOUMISSION
}
