package com.kimia.bcrg_integration.audit;

import java.time.Instant;

/**
 * Accusé de réception d'une transmission acceptée par la BCRG (ticket 7.3) : la réponse de l'API,
 * conservée telle qu'elle a été reçue (aux secrets masqués près) et retrouvable par sa
 * {@link AuditTarget}.
 * <p>C'est la pièce justificative d'un envoi : elle prouve <em>quoi</em> a été transmis,
 * <em>quand</em>, et <em>ce que la BCRG a répondu</em>.
 *
 * @param cible      fichier / feuille / période concernés
 * @param horodatage instant de réception
 * @param httpStatus code HTTP de la réponse (2xx)
 * @param corps      corps de la réponse, masqué
 */
public record Acknowledgement(AuditTarget cible, Instant horodatage, int httpStatus, String corps) {
}
