package com.kimia.bcrg_integration.audit;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Acteur courant de la piste d'audit, le temps d'un traitement.
 * <p>Le module transmet sous <strong>un compte de service unique</strong> côté SIRYF (la BCRG
 * raisonne par institution, pas par personne), mais la trace réglementaire (§9) doit dire
 * <strong>qui</strong> a déclenché l'envoi : dans le workflow SIRYF, c'est le
 * <em>transmetteur</em> dont l'approbation (étape finale) lance la transmission.
 * <p>Porté par un {@link ThreadLocal} plutôt que par un paramètre supplémentaire sur
 * {@code DossierIntegrationService → RetryingIntegrationService → IntegrationService} : l'identité
 * de l'appelant est une donnée transverse (comme le MDC des journaux), elle n'a rien à faire dans
 * la signature du pipeline. Le traitement est synchrone dans le fil de la requête, la portée est
 * donc exacte, et {@link #avecActeur} restaure toujours l'état précédent.
 */
public final class AuditContext {

    private static final ThreadLocal<String> ACTEUR = new ThreadLocal<>();

    private AuditContext() {}

    /**
     * Exécute {@code action} en attribuant ses événements d'audit à {@code acteur}.
     * Un acteur nul ou vide laisse le compte de service par défaut s'appliquer.
     */
    public static <T> T avecActeur(String acteur, Supplier<T> action) {
        String precedent = ACTEUR.get();
        if (acteur != null && !acteur.isBlank()) {
            ACTEUR.set(acteur.trim());
        }
        try {
            return action.get();
        } finally {
            if (precedent == null) {
                ACTEUR.remove();
            } else {
                ACTEUR.set(precedent);
            }
        }
    }

    /** Acteur attribué au traitement en cours, s'il a été précisé. */
    public static Optional<String> acteurCourant() {
        return Optional.ofNullable(ACTEUR.get());
    }
}
