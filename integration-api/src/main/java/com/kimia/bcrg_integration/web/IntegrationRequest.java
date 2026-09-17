package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Corps de la demande d'intégration d'un dossier (Lot 8, ticket 8.1).
 * <p>Les champs optionnels ont un défaut explicite plutôt qu'un {@code null} qui se propagerait dans
 * le pipeline : toutes les feuilles, statut {@code CREATION}, fichier {@code SIGIMF}.
 *
 * @param fichier     chemin du classeur sur le serveur (dépôt Alfresco monté, partage réseau…)
 * @param feuille     feuille à traiter, ou « TOUS » / vide pour toutes les feuilles couvertes
 * @param annee       année de la période ; omise = déduite de la « Date des rapports » du classeur
 * @param mois        mois de la période ({@code dateArrete} = dernier jour de ce mois) ; omis = déduit
 * @param statut      CREATION (défaut), MODIFICATION, ANNULER ou ERREUR
 * @param codeFichier code du fichier réglementaire ; défaut « AUTO » = déduit des feuilles du classeur
 * @param acteur      compte dont l'approbation a déclenché l'envoi (dans le workflow SIRYF, le
 *                    <strong>transmetteur</strong> qui exécute l'étape finale) — inscrit dans la
 *                    piste d'audit comme acteur de la transmission ; facultatif
 */
public record IntegrationRequest(
        @NotBlank(message = "le chemin du classeur est obligatoire") String fichier,
        String feuille,
        @Min(2000) @Max(2100) Integer annee,
        @Min(1) @Max(12) Integer mois,
        String statut,
        String codeFichier,
        String acteur) {

    /** Feuille demandée, ou « TOUS » si non précisée. */
    public String feuilleOuToutes() {
        return (feuille == null || feuille.isBlank()) ? "TOUS" : feuille.trim();
    }

    /** Code du fichier réglementaire, détection automatique par défaut. */
    public String codeFichierOuDefaut() {
        return (codeFichier == null || codeFichier.isBlank())
                ? DossierIntegrationService.CODE_FICHIER_AUTO
                : codeFichier.trim();
    }

    /**
     * Statut de transmission demandé, CREATION par défaut.
     *
     * @throws IllegalArgumentException si le statut fourni n'est pas reconnu (→ HTTP 400)
     */
    public StatutEnum statutOuCreation() {
        if (statut == null || statut.isBlank()) {
            return StatutEnum.CREATION;
        }
        return StatutEnum.fromValue(statut.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
