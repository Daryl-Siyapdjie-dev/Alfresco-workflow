package com.kimia.bcrg_integration.audit;

import java.time.OffsetDateTime;

/**
 * Cible d'un événement d'audit : <em>quelle donnée</em> était concernée (ticket 7.1).
 * <p>Le triplet {@code codeFichier + feuille + dateArrete} identifie une transmission de façon
 * stable — c'est aussi la clé sous laquelle on retrouve l'accusé de réception correspondant
 * ({@link AcknowledgementStore}, ticket 7.3). Le chemin physique du classeur n'en fait
 * volontairement pas partie : le même contenu peut être rejoué depuis un autre emplacement.
 *
 * @param codeFichier code du fichier réglementaire (ex. « SIGIMF »), {@code null} si inconnu à ce niveau
 * @param feuille     nom de la feuille traitée, {@code null} pour un événement de niveau dossier
 * @param dateArrete  période de la transmission (dernier jour du mois)
 */
public record AuditTarget(String codeFichier, String feuille, OffsetDateTime dateArrete) {

    /** Cible de niveau dossier (pas de feuille précise). */
    public static AuditTarget dossier(String codeFichier, OffsetDateTime dateArrete) {
        return new AuditTarget(codeFichier, null, dateArrete);
    }
}
