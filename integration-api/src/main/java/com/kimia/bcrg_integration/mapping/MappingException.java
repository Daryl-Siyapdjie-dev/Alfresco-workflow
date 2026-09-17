package com.kimia.bcrg_integration.mapping;

/**
 * Erreur de transformation d'une feuille vers son modèle CIBLE : colonne obligatoire absente,
 * valeur non convertible, etc. Distincte de {@code BcrgApiException} (erreurs de l'API distante).
 */
public class MappingException extends RuntimeException {
    public MappingException(String message) {
        super(message);
    }
}
