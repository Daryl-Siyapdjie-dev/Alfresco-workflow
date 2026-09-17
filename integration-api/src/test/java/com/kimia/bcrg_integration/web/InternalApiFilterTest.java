package com.kimia.bcrg_integration.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ticket 8.2 : l'API interne n'est pas ouverte. On fige ici la posture par défaut — <strong>fermée</strong>
 * sans configuration — parce que c'est elle qui protège une installation où personne n'a pensé à
 * renseigner un jeton.
 */
class InternalApiFilterTest {

    private static final String URI_INTERNE = "/api/internal/integration/sigimf";

    private MockHttpServletRequest requete(String uri, String adresse) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setRequestURI(uri);
        r.setRemoteAddr(adresse);
        return r;
    }

    private MockFilterChain passe(InternalApiFilter filtre, MockHttpServletRequest requete,
                                  MockHttpServletResponse reponse) throws Exception {
        MockFilterChain chaine = new MockFilterChain();
        filtre.doFilter(requete, reponse, chaine);
        return chaine;
    }

    @Test
    void sans_jeton_une_requete_locale_passe() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("", false);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        MockFilterChain chaine = passe(filtre, requete(URI_INTERNE, "127.0.0.1"), reponse);

        assertNotNull(chaine.getRequest(), "la requête locale doit atteindre le contrôleur");
        assertEquals(200, reponse.getStatus());
    }

    @Test
    void sans_jeton_une_requete_distante_est_refusee() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("", false);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        MockFilterChain chaine = passe(filtre, requete(URI_INTERNE, "203.0.113.5"), reponse);

        assertNull(chaine.getRequest(), "la requête distante ne doit pas atteindre le contrôleur");
        assertEquals(403, reponse.getStatus());
    }

    @Test
    void avec_jeton_l_entete_correct_passe_et_l_entete_faux_est_refuse() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("jeton-interne", false);

        MockHttpServletRequest bonne = requete(URI_INTERNE, "203.0.113.5");
        bonne.addHeader(InternalApiFilter.ENTETE_JETON, "jeton-interne");
        MockHttpServletResponse reponseOk = new MockHttpServletResponse();
        assertNotNull(passe(filtre, bonne, reponseOk).getRequest());
        assertEquals(200, reponseOk.getStatus());

        MockHttpServletRequest mauvaise = requete(URI_INTERNE, "203.0.113.5");
        mauvaise.addHeader(InternalApiFilter.ENTETE_JETON, "jeton-faux");
        MockHttpServletResponse reponseKo = new MockHttpServletResponse();
        assertNull(passe(filtre, mauvaise, reponseKo).getRequest());
        assertEquals(401, reponseKo.getStatus());
    }

    @Test
    void avec_jeton_l_entete_absent_est_refuse() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("jeton-interne", false);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        assertNull(passe(filtre, requete(URI_INTERNE, "127.0.0.1"), reponse).getRequest(),
                "même en local, un jeton configuré est exigé");
        assertEquals(401, reponse.getStatus());
        assertFalse(reponse.getContentAsString().contains("jeton-interne"),
                "la réponse ne doit pas divulguer la valeur attendue");
    }

    @Test
    void allow_remote_leve_la_restriction_d_adresse() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("", true);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        assertNotNull(passe(filtre, requete(URI_INTERNE, "203.0.113.5"), reponse).getRequest());
        assertEquals(200, reponse.getStatus());
    }

    @Test
    void les_url_hors_api_interne_ne_sont_pas_filtrees() throws Exception {
        InternalApiFilter filtre = new InternalApiFilter("", false);
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        assertNotNull(passe(filtre, requete("/actuator/health", "203.0.113.5"), reponse).getRequest());
        assertEquals(200, reponse.getStatus());
    }
}
