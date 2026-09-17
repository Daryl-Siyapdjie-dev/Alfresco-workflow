package com.kimia.bcrg_integration.client;

import com.kimia.bcrg_integration.client.dto.TransmissionModel;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client des rapports de suivi de transmission (réconciliation).
 * <p>Quatre familles : situation, erreurs, chargées, non chargées. Chaque famille a une
 * version « unitaire » ({@code /rapport}) et une version « liste » ({@code /rapport/liste}).
 * <p>Le corps est un {@link TransmissionModel} (filtre : type de fichier, période, dateArrete…).
 * La réponse est renvoyée en JSON brut ({@code String}) car l'API la déclare générique ;
 * elle sera typée quand sa structure réelle sera connue. Erreurs 4xx/5xx → BcrgApiException (socle).
 */
@Component
public class ReconciliationClient {

    private final RestClient client;


    public ReconciliationClient(RestClient bcrgAuthenticatedRestClient) {
        this.client = bcrgAuthenticatedRestClient;
    } 

    // ---- Situation des transmissions ----
    public String situation(TransmissionModel body) {
        return post("/situationtransmission/rapport", body);
    }
    public String situationListe(TransmissionModel body) {
        return post("/situationtransmission/rapport/liste", body);
    }

    // ---- Erreurs de transmission ----
    public String erreurs(TransmissionModel body) {
        return post("/erreurtransmission/rapport", body);
    }
    public String erreursListe(TransmissionModel body) {
        return post("/erreurtransmission/rapport/liste", body);
    }

    // ---- Transmissions chargées ----
    public String chargees(TransmissionModel body) {
        return post("/chargementtransmission/rapport", body);
    }
    public String chargeesListe(TransmissionModel body) {
        return post("/chargementtransmission/rapport/liste", body);
    }

    // ---- Transmissions NON chargées ----
    public String nonChargees(TransmissionModel body) {
        return post("/nonchargementtransmission/rapport", body);
    }
    public String nonChargeesListe(TransmissionModel body) {
        return post("/nonchargementtransmission/rapport/liste", body);
    }

    // ---- Méthode privée commune ----
    private String post(String uri, TransmissionModel body) {
        return client.post()
                .uri(uri)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}