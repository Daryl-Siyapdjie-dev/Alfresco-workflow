package com.kimia.bcrg_integration.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Injecte l'en-tête {@code Authorization: Bearer <token>} sur chaque appel authentifié.
 * <p>Si le serveur rejette le token (401/403) alors qu'il était localement « valide »,
 * invalide le token, en obtient un neuf et rejoue la requête <b>une seule fois</b>.
 * <p>Ne journalise jamais le token ni le corps de la requête (données confidentielles) :
 * seuls la méthode, l'URI et la taille du corps sont tracés en DEBUG.
 */
@Component
public class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenInterceptor.class);

    private final AuthClient authClient;
    private final TokenStore tokenStore;

    public BearerTokenInterceptor(AuthClient authClient, TokenStore tokenStore) {
        this.authClient = authClient;
        this.tokenStore = tokenStore;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(authClient.getValidToken());
        if (log.isDebugEnabled()) {
            log.debug("→ {} {} ({} octets)", request.getMethod(), request.getURI(), body.length);
        }

        ClientHttpResponse response = execution.execute(request, body);

        if (isAuthFailure(response.getStatusCode())) {
            log.warn("Réponse {} sur {} {} — invalidation du token et re-essai unique.",
                    response.getStatusCode(), request.getMethod(), request.getURI());
            response.close();
            tokenStore.invalidate();
            request.getHeaders().setBearerAuth(authClient.getValidToken());
            response = execution.execute(request, body);
        }
        return response;
    }

    private boolean isAuthFailure(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }
}
