package com.kimia.bcrg_integration.audit;

import java.time.Instant;

/**
 * Événement d'audit (ticket 7.1) : <strong>quand</strong>, <strong>qui</strong>, <strong>quoi</strong>,
 * <strong>sur quoi</strong>, <strong>avec quel résultat</strong> — la trace réglementaire d'une étape
 * du pipeline (§9).
 * <p>Le {@code detail} est systématiquement passé par {@link AuditRedactor} avant d'arriver ici :
 * aucun jeton ni mot de passe ne doit se retrouver dans la piste d'audit (ticket 7.4).
 *
 * @param horodatage   instant de l'événement (UTC)
 * @param acteur       compte à l'origine de l'action (compte de service du module par défaut)
 * @param action       étape concernée
 * @param cible        donnée concernée (fichier / feuille / période)
 * @param resultat     issue de l'étape
 * @param httpStatus   code HTTP quand l'étape a appelé l'API BCRG, sinon {@code null}
 * @param detail       message lisible, déjà masqué des données sensibles
 * @param dureeMillis  durée de l'étape en millisecondes (0 si non mesurée)
 */
public record AuditEvent(Instant horodatage, String acteur, AuditAction action, AuditTarget cible,
                         Resultat resultat, Integer httpStatus, String detail, long dureeMillis) {

    /** Issue d'une étape : réussie, en échec, ou volontairement non exécutée (idempotence). */
    public enum Resultat { SUCCES, ECHEC, IGNORE }

    public boolean isEchec() {
        return resultat == Resultat.ECHEC;
    }
}
