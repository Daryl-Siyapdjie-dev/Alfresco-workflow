package com.kimia.bcrg_integration.client;

import org.springframework.http.ResponseEntity;

/**
 * Abstraction d'envoi HTTP vers la BCRG : poste un corps JSON sur un endpoint et renvoie la réponse.
 * <p>Permet au pipeline (registre de jobs) de dépendre d'une interface simple plutôt que du
 * {@code RestClient} concret — ce qui rend l'assemblage des jobs testable hors-ligne (envoi factice).
 */
public interface BcrgSender {

    ResponseEntity<String> post(String uri, Object body);
}
