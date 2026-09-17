package com.kimia.bcrg_integration.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.ssl.SslBundles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Le contrôle TLS de démarrage doit rester <strong>silencieux et instantané</strong> quand il n'y a
 * rien à vérifier.
 *
 * <p>C'est ce qui le tient hors des tests : {@code @SpringBootTest} charge le contexte complet et
 * déclenche donc {@code ApplicationReadyEvent}. Sans la garde « pas d'identifiants, pas de
 * contrôle », chaque exécution de la suite ouvrirait une connexion vers la BCRG — lente, et rouge
 * dès qu'on travaille hors ligne.
 *
 * <p>Le cas nominal, lui, ne se teste pas ici : il n'a de sens que face à un vrai serveur, et c'est
 * précisément ce que la ligne « TLS BCRG vérifié » constate au démarrage (voir DEPLOIEMENT.md §4).
 */
class ControleTlsTest {

    private static final SslBundles AUCUN_BUNDLE = new SslBundles() {
        @Override public org.springframework.boot.ssl.SslBundle getBundle(String name) {
            throw new IllegalStateException("aucun bundle ne devait être demandé");
        }
        @Override public void addBundleUpdateHandler(String name,
                                                     java.util.function.Consumer<org.springframework.boot.ssl.SslBundle> handler) { }
        @Override public void addBundleRegisterHandler(
                java.util.function.BiConsumer<String, org.springframework.boot.ssl.SslBundle> handler) { }
        @Override public java.util.List<String> getBundleNames() { return java.util.List.of(); }
    };

    private static BcrgApiProperties proprietes(String username, String baseUrl) {
        return new BcrgApiProperties(baseUrl, username, "secret",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 60, null);
    }

    @Test
    @Timeout(3)
    void sans_identifiants_le_controle_ne_touche_pas_au_reseau() {
        // adresse volontairement injoignable : si le contrôle s'exécutait, l'épreuve dépasserait
        // son délai au lieu de rendre la main aussitôt
        ControleTls controle = new ControleTls(
                proprietes("", "https://serveur.invalide.test:8186"), AUCUN_BUNDLE);

        assertDoesNotThrow(controle::verifier);
    }

    @Test
    @Timeout(3)
    void une_base_url_non_https_n_a_pas_de_confiance_a_verifier() {
        ControleTls controle = new ControleTls(
                proprietes("compte", "http://localhost:9999"), AUCUN_BUNDLE);

        assertDoesNotThrow(controle::verifier);
    }
}
