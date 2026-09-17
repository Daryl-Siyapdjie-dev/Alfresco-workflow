package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.exception.BcrgApiException;
import com.kimia.bcrg_integration.mapping.MappingException;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduction des exceptions du module en réponses HTTP de l'API interne (Lot 8).
 * <p>Le choix des codes suit la classification des issues du §7 : ce qui relève d'une <em>donnée</em>
 * ou d'une <em>demande</em> mal formée est imputé à l'appelant (4xx), ce qui vient de l'API BCRG est
 * signalé comme une défaillance d'amont (502) — pour qu'un superviseur distingue « le fichier est
 * mauvais » de « la BCRG a refusé » sans lire les journaux.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Demande mal formée (champ obligatoire absent, période hors bornes). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> champ(f) + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(" ; "));
        return reponse(HttpStatus.BAD_REQUEST, detail);
    }

    /** Paramètre invalide : classeur introuvable, statut de transmission inconnu. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> parametre(IllegalArgumentException e) {
        return reponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Classeur illisible : rien n'a pu être traité. */
    @ExceptionHandler(DossierIntegrationException.class)
    public ResponseEntity<ApiError> dossier(DossierIntegrationException e) {
        return reponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Donnée non transformable : la structure du classeur ne correspond pas à l'attendu. */
    @ExceptionHandler(MappingException.class)
    public ResponseEntity<ApiError> mapping(MappingException e) {
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * L'API BCRG a échoué là où le pipeline ne l'a pas classée lui-même (chaîne de transmission,
     * authentification) : défaillance d'amont, pas du module.
     */
    @ExceptionHandler(BcrgApiException.class)
    public ResponseEntity<ApiError> bcrg(BcrgApiException e) {
        return reponse(HttpStatus.BAD_GATEWAY, "API BCRG " + e.getHttpStatus() + " — " + e.getMessage());
    }

    /** Filet de sécurité : tout le reste est un incident du module. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inattendue(Exception e) {
        log.error("Erreur inattendue sur l'API interne", e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private static String champ(FieldError erreur) {
        return erreur.getField();
    }

    private static ResponseEntity<ApiError> reponse(HttpStatus statut, String detail) {
        return ResponseEntity.status(statut).body(ApiError.of(statut, detail));
    }
}
