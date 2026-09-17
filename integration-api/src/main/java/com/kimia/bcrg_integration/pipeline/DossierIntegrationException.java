package com.kimia.bcrg_integration.pipeline;

/**
 * Le dossier n'a pas pu être ouvert ou parcouru (fichier introuvable, format illisible, verrou).
 * <p>Distincte des erreurs de <em>feuille</em>, qui sont classées dans un {@link IntegrationResult}
 * sans interrompre le dossier : ici, rien n'a pu être traité du tout.
 */
public class DossierIntegrationException extends RuntimeException {

    public DossierIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
