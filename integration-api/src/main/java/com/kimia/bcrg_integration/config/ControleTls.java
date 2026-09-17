package com.kimia.bcrg_integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Vérification de la <strong>confiance TLS</strong> envers l'API BCRG, au démarrage.
 *
 * <p><strong>Le problème qu'elle résout.</strong> Le module ne parle à la BCRG qu'au moment d'une
 * remise — soit une fois par mois. Si la JVM ne fait pas confiance au certificat du port
 * {@code :8186}, personne ne l'apprend avant ce jour-là, et le message reçu alors est
 * {@code PKIX path building failed: unable to find valid certification path}, qui ne dit ni quel
 * certificat a été présenté, ni par qui il a été émis, ni quoi faire. Ce contrôle pose la question
 * au démarrage et répond en clair.
 *
 * <p><strong>Ce qu'il sait distinguer, et pourquoi c'est utile.</strong> Il journalise
 * l'<em>émetteur</em> du certificat effectivement présenté. Sur le poste de développement, cet
 * émetteur est « Avast Web/Mail Shield Root » : l'antivirus s'interpose et présente un certificat
 * qu'il a fabriqué lui-même — d'où l'échec, et d'où le contournement
 * {@code -Djavax.net.ssl.trustStoreType=Windows-ROOT} qui n'a de sens que là. Sur un serveur, un
 * émetteur inattendu désigne un proxy d'inspection ; un émetteur attendu mais inconnu de la JVM
 * désigne une autorité privée, à installer. Ce ne sont pas les mêmes remèdes, et sans l'émetteur on
 * ne peut pas les distinguer.
 *
 * <p><strong>N'interrompt jamais le démarrage.</strong> Un module de télétransmission doit démarrer
 * même quand la BCRG est injoignable : ses journaux, son API interne et sa piste d'audit restent
 * utiles. Un échec ici est un avertissement, pas une panne.
 */
@Component
public class ControleTls {

    private static final Logger log = LoggerFactory.getLogger(ControleTls.class);

    /** Court : ce contrôle est un renseignement, il ne doit pas retarder le démarrage. */
    private static final Duration DELAI = Duration.ofSeconds(10);

    private static final int PORT_HTTPS_PAR_DEFAUT = 443;

    private final BcrgApiProperties props;
    private final SslBundles sslBundles;

    public ControleTls(BcrgApiProperties props, SslBundles sslBundles) {
        this.props = props;
        this.sslBundles = sslBundles;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        // Sans identifiants, ce module ne transmettra rien : il n'y a pas de confiance à vérifier.
        // C'est aussi ce qui garde le contrôle hors des tests, qui chargent le contexte complet
        // mais n'ont ni compte BCRG ni réseau à solliciter.
        if (props.username() == null || props.username().isBlank()) {
            log.info("Contrôle TLS ignoré : aucun identifiant BCRG configuré");
            return;
        }
        URI uri = URI.create(props.baseUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            log.info("Contrôle TLS ignoré : {} n'est pas en HTTPS", props.baseUrl());
            return;
        }
        int port = uri.getPort() > 0 ? uri.getPort() : PORT_HTTPS_PAR_DEFAUT;

        try (SSLSocket socket = ouvrir(uri.getHost(), port)) {
            socket.startHandshake();                       // c'est ici que la confiance se joue
            X509Certificate serveur =
                    (X509Certificate) socket.getSession().getPeerCertificates()[0];
            log.info("TLS BCRG vérifié — {}:{} présente « {} », émis par « {} », valable jusqu'au {}",
                    uri.getHost(), port, nom(serveur.getSubjectX500Principal().getName()),
                    nom(serveur.getIssuerX500Principal().getName()), serveur.getNotAfter());
            avertirSiProcheDeExpiration(serveur);

        } catch (Exception echec) {
            expliquer(uri.getHost(), port, echec);
        }
    }

    private SSLSocket ouvrir(String hote, int port) throws Exception {
        SSLSocketFactory fabrique = contexte().getSocketFactory();
        Socket brut = new Socket();
        brut.connect(new InetSocketAddress(hote, port), (int) DELAI.toMillis());
        SSLSocket socket = (SSLSocket) fabrique.createSocket(brut, hote, port, true);
        socket.setSoTimeout((int) DELAI.toMillis());
        return socket;
    }

    /** Le même contexte que les appels réels : contrôler autre chose que ce qui sert n'apprend rien. */
    private SSLContext contexte() throws Exception {
        String bundle = props.sslBundle();
        if (bundle == null || bundle.isBlank()) {
            return SSLContext.getDefault();
        }
        SslBundle configure = sslBundles.getBundle(bundle);
        return configure.createSslContext();
    }

    /**
     * Traduit l'échec en cause probable et en remède. Le message est volontairement long : il sera
     * lu une fois, par quelqu'un qui déploie et qui n'a pas suivi cette histoire.
     */
    private void expliquer(String hote, int port, Exception echec) {
        String cause = racine(echec);
        boolean confiance = cause.contains("PKIX") || cause.contains("certification path")
                || cause.contains("unable to find valid");

        if (!confiance) {
            log.warn("BCRG {}:{} injoignable au démarrage ({}). Le module démarre quand même ; "
                    + "la remise échouera tant que le lien n'est pas rétabli.", hote, port, cause);
            return;
        }

        log.error("""
                TLS BCRG NON VÉRIFIÉ — {}:{} présente un certificat auquel cette JVM ne fait pas confiance.
                  Cause    : {}
                  Émetteur : {}
                  Toute remise échouera. Deux situations, deux remèdes :
                   1. un antivirus ou un proxy inspecte le HTTPS (l'émetteur ci-dessus le nomme) —
                      soit l'exclure de cet hôte, soit ajouter SA racine au truststore ;
                   2. la BCRG signe avec une autorité privée — importer sa chaîne dans un keystore,
                      le déclarer sous spring.ssl.bundle.jks.bcrg, puis bcrg.api.ssl-bundle=bcrg.
                  Voir DEPLOIEMENT.md, « Faire confiance au certificat BCRG ».""",
                hote, port, cause, emetteurPresente(hote, port));
    }

    /**
     * Émetteur du certificat réellement présenté, obtenu <strong>sans vérifier</strong> la chaîne.
     * <p>C'est le seul renseignement qui permette de distinguer une interception d'une autorité
     * privée — et il n'est justement pas disponible quand la vérification échoue. Aucun appel
     * applicatif n'est fait sur cette connexion : elle sert à lire un certificat, puis se referme.
     */
    private String emetteurPresente(String hote, int port) {
        try {
            SSLContext sansControle = SSLContext.getInstance("TLS");
            sansControle.init(null, new javax.net.ssl.TrustManager[]{new TousAcceptes()}, null);
            Socket brut = new Socket();
            brut.connect(new InetSocketAddress(hote, port), (int) DELAI.toMillis());
            try (SSLSocket socket = (SSLSocket) sansControle.getSocketFactory()
                    .createSocket(brut, hote, port, true)) {
                socket.setSoTimeout((int) DELAI.toMillis());
                socket.startHandshake();
                X509Certificate serveur =
                        (X509Certificate) socket.getSession().getPeerCertificates()[0];
                return nom(serveur.getIssuerX500Principal().getName());
            }
        } catch (Exception illisible) {
            return "(non lisible : " + racine(illisible) + ")";
        }
    }

    private void avertirSiProcheDeExpiration(X509Certificate certificat) {
        try {
            certificat.checkValidity();
        } catch (CertificateExpiredException expire) {
            log.warn("Le certificat BCRG a EXPIRÉ le {} — les remises vont échouer",
                    certificat.getNotAfter());
        } catch (CertificateNotYetValidException pasEncore) {
            log.warn("Le certificat BCRG n'est valable qu'à partir du {}", certificat.getNotBefore());
        }
    }

    private static String racine(Throwable e) {
        Throwable courant = e;
        while (courant.getCause() != null && courant.getCause() != courant) {
            courant = courant.getCause();
        }
        return courant.getClass().getSimpleName() + " : " + courant.getMessage();
    }

    /** Garde le CN d'un nom X.500, plus lisible qu'« OU=…, O=…, CN=… » dans un journal. */
    private static String nom(String x500) {
        for (String champ : x500.split(",")) {
            String c = champ.trim();
            if (c.regionMatches(true, 0, "CN=", 0, 3)) {
                return c.substring(3);
            }
        }
        return x500;
    }

    /**
     * Gestionnaire de confiance qui n'en est pas un : il sert <strong>uniquement</strong> à lire
     * l'émetteur d'un certificat déjà refusé, pour pouvoir le nommer dans le diagnostic. Aucune
     * requête applicative n'emprunte cette connexion — la voie de production reste
     * {@link RestClientConfig}, qui vérifie normalement.
     */
    private static final class TousAcceptes implements javax.net.ssl.X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chaine, String type) { }
        @Override public void checkServerTrusted(X509Certificate[] chaine, String type) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
