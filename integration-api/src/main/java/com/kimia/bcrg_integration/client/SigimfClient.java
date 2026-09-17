package com.kimia.bcrg_integration.client;

import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfInformationGeneraleString;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client d'envoi des feuilles SIGIMF vers la BCRG. Implémente {@link BcrgSender} pour un POST
 * générique {@code (uri, body)} utilisé par le registre de jobs du pipeline.
 */
@Component
public class SigimfClient implements BcrgSender {

    private final RestClient client;

    public SigimfClient(RestClient bcrgAuthenticatedRestClient) {
        this.client = bcrgAuthenticatedRestClient;
    }

    @Override
    public ResponseEntity<String> post(String uri, Object body) {
        return client.post()
                .uri(uri)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    /** Raccourci historique (feuille M.0.INFO.G). */
    public ResponseEntity<String> envoyerM0Infog(DataModelImfSigImfInformationGeneraleString body) {
        return post("/api/sigimf/m0infog", body);
    }
}
