package com.kimia.bcrg_integration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Contrôle d'accès de l'API interne (Lot 8, ticket 8.2). Deux niveaux, du plus fort au plus simple :
 * <ol>
 *   <li>si {@code bcrg.internal.token} est renseigné, l'en-tête {@code X-Internal-Token} doit le
 *       reproduire exactement — comparaison à temps constant, pour ne pas divulguer le jeton par
 *       la durée de la réponse ;</li>
 *   <li>sinon, seules les requêtes venant de la <strong>machine locale</strong> passent — configuration
 *       par défaut « fermée », adaptée à un module déployé derrière le workflow documentaire.
 *       {@code bcrg.internal.allow-remote=true} lève cette restriction (à réserver aux réseaux déjà
 *       cloisonnés).</li>
 * </ol>
 * Ce filtre ne couvre que {@code /api/internal/} : l'actuator et les éventuels endpoints publics
 * gardent leur propre configuration.
 */
@Component
public class InternalApiFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiFilter.class);

    static final String PREFIXE = "/api/internal/";
    static final String ENTETE_JETON = "X-Internal-Token";

    private final String jeton;
    private final boolean autoriserDistant;

    public InternalApiFilter(@Value("${bcrg.internal.token:}") String jeton,
                             @Value("${bcrg.internal.allow-remote:false}") boolean autoriserDistant) {
        this.jeton = jeton;
        this.autoriserDistant = autoriserDistant;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !requete.getRequestURI().startsWith(PREFIXE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        if (jeton != null && !jeton.isBlank()) {
            if (!jetonValide(requete.getHeader(ENTETE_JETON))) {
                refuser(requete, reponse, HttpStatus.UNAUTHORIZED,
                        "En-tête " + ENTETE_JETON + " absent ou invalide");
                return;
            }
        } else if (!autoriserDistant && !estLocale(requete.getRemoteAddr())) {
            refuser(requete, reponse, HttpStatus.FORBIDDEN,
                    "API interne restreinte à la machine locale : configurer bcrg.internal.token "
                            + "ou bcrg.internal.allow-remote");
            return;
        }
        chaine.doFilter(requete, reponse);
    }

    /** Comparaison à temps constant (pas de court-circuit sur le premier caractère différent). */
    private boolean jetonValide(String recu) {
        if (recu == null) {
            return false;
        }
        return MessageDigest.isEqual(jeton.getBytes(StandardCharsets.UTF_8),
                recu.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean estLocale(String adresse) {
        if (adresse == null) {
            return false;
        }
        try {
            return InetAddress.getByName(adresse).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Réponse d'erreur en JSON, sans jamais renvoyer la valeur attendue du jeton. */
    private void refuser(HttpServletRequest requete, HttpServletResponse reponse,
                         HttpStatus statut, String message) throws IOException {
        log.warn("Accès refusé à {} depuis {} : {}", requete.getRequestURI(), requete.getRemoteAddr(), message);
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write("{\"status\":" + statut.value()
                + ",\"erreur\":\"" + statut.getReasonPhrase() + "\""
                + ",\"detail\":\"" + message.replace("\"", "'") + "\"}");
    }
}
