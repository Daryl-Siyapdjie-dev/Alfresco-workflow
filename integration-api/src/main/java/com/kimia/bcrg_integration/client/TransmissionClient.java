package com.kimia.bcrg_integration.client;

import com.kimia.bcrg_integration.client.dto.TransmissionModel;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client de la chaîne de transmission SIRYF : vérification → autorisation → soumission.
 * <p>Tous les appels envoient un {@link TransmissionModel} et renvoient le message (texte)
 * retourné par l'API. Les erreurs 4xx/5xx sont traduites en {@code BcrgApiException} par
 * le socle (gestion centralisée), donc pas de try/catch ici.
 * <p>Les variantes {@code *Imf} correspondent au périmètre IMF (compte de service actuel).
 */
@Component
public class TransmissionClient {

    private final RestClient client;

    public TransmissionClient(RestClient bcrgAuthenticatedRestClient) {
        this.client = bcrgAuthenticatedRestClient;
    }

    // ---- Chaîne IMF ----

    /** 1) Vérification des feuilles (endpoint commun à tous les types). */
    public String verification(TransmissionModel body) {
        return client.post()
                .uri("/etats/transmission/verification")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /** 2) Autorisation / contrôle — variante IMF. */
    public String autorisationImf(TransmissionModel body) {
        return client.post()
                .uri("/etats/transmissionImf/autorisation")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /** 3) Soumission — variante IMF. */
    public String soumissionImf(TransmissionModel body) {
        return client.post()
                .uri("/etats/transmission/soumissionImf")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    // ---- Variantes génériques (banque / autres), pour complétude ----

    public String autorisation(TransmissionModel body) {
        return client.post().uri("/etats/transmission/autorisation")
                .body(body).retrieve().body(String.class);
    }

    public String soumission(TransmissionModel body) {
        return client.post().uri("/etats/transmission/soumission")
                .body(body).retrieve().body(String.class);
    }

    public String soumissionListe(TransmissionModel body) {
        return client.post().uri("/etats/transmission/soumission/liste")
                .body(body).retrieve().body(String.class);
    }
}