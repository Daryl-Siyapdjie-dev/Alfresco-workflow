package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.audit.AuditRedactor;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Corps d'erreur uniforme de l'API interne. Le {@code detail} passe par {@link AuditRedactor} :
 * un message d'exception peut contenir une URL ou un en-tête porteur d'un jeton, et une réponse
 * d'erreur n'a pas plus le droit de le divulguer qu'un journal (§8, ticket 7.4).
 *
 * @param status     code HTTP
 * @param erreur     libellé du statut
 * @param detail     message lisible, masqué de ses secrets
 * @param horodatage instant de la réponse
 */
public record ApiError(int status, String erreur, String detail, Instant horodatage) {

    public static ApiError of(HttpStatus statut, String detail) {
        return new ApiError(statut.value(), statut.getReasonPhrase(),
                AuditRedactor.mask(detail), Instant.now());
    }
}
