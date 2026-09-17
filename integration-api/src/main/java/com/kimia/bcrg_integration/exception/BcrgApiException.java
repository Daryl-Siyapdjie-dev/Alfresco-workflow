package com.kimia.bcrg_integration.exception;

import com.kimia.bcrg_integration.client.dto.MessageResponse;
import org.springframework.http.HttpStatusCode;

/**
 * Exception levée lorsqu'un appel à l'API BCRG renvoie un statut d'erreur (4xx/5xx).
 * <p>Porte le code HTTP et, si l'API l'a fourni, le corps {@link MessageResponse}
 * ({@code statusCode, message, description, exception}) pour un diagnostic exploitable
 * par les couches supérieures (pipeline, reprise, journalisation).
 */
public class BcrgApiException extends RuntimeException {

    private final int httpStatus;
    private final Integer apiStatusCode;
    private final String apiMessage;
    private final String apiDescription;
    private final String apiException;

    public BcrgApiException(HttpStatusCode httpStatus, MessageResponse body, String rawBody) {
        super(buildMessage(httpStatus, body, rawBody));
        this.httpStatus = httpStatus.value();
        if (body != null) {
            this.apiStatusCode = body.getStatusCode();
            this.apiMessage = body.getMessage();
            this.apiDescription = body.getDescription();
            this.apiException = body.getException();
        } else {
            this.apiStatusCode = null;
            this.apiMessage = null;
            this.apiDescription = null;
            this.apiException = null;
        }
    }

    private static String buildMessage(HttpStatusCode httpStatus, MessageResponse body, String rawBody) {
        if (body != null && body.getMessage() != null) {
            return "BCRG API " + httpStatus.value() + " : " + body.getMessage()
                    + (body.getDescription() != null ? " (" + body.getDescription() + ")" : "");
        }
        String snippet = (rawBody != null && !rawBody.isBlank()) ? " : " + rawBody : "";
        return "BCRG API " + httpStatus.value() + snippet;
    }

    /** true pour un 4xx (erreur fonctionnelle/données — pas de rejeu automatique, cf. §7). */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }

    /** true pour un 5xx (erreur technique/transitoire — candidate au rejeu, cf. §7). */
    public boolean isServerError() {
        return httpStatus >= 500 && httpStatus < 600;
    }

    public int getHttpStatus()        { return httpStatus; }
    public Integer getApiStatusCode() { return apiStatusCode; }
    public String getApiMessage()     { return apiMessage; }
    public String getApiDescription() { return apiDescription; }
    public String getApiException()   { return apiException; }
}
