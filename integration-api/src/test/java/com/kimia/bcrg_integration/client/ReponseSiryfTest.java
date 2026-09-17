package com.kimia.bcrg_integration.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lecture des contrôles de cohérence renvoyés par SIRYF. Les corps ci-dessous sont ceux réellement
 * observés pendant la recette du 27/08/2026 — on ne teste pas un format supposé.
 * <p>Le principe qui gouverne cette classe : <strong>ne jamais transformer une transmission réussie
 * en échec parce qu'on n'a pas su lire la réponse.</strong> Tout ce qui n'est pas un tableau
 * d'erreurs identifiable vaut « rien à signaler ».
 */
class ReponseSiryfTest {

    @Test
    void un_succes_sans_controle_ne_signale_rien() {
        String corps = "{\"erreurs\":[],\"description\":\"Traitement effectué avec succès\","
                + "\"statutCode\":\"OK\"}";

        assertTrue(ReponseSiryf.lire(corps).sansErreur());
    }

    /** Le cas qui motive toute la classe : accepté (HTTP 200) mais des lignes sont refusées. */
    @Test
    void un_succes_porteur_d_erreurs_les_remonte() {
        String corps = "{\"erreurs\":["
                + "{\"ligne\":\"4\",\"erreur\":\" Code M.I.B.207 est introuvable \"},"
                + "{\"ligne\":\"9\",\"erreur\":\" Ecart de coherence sur le total \"}],"
                + "\"description\":\"Traitement effectué avec succès\",\"statutCode\":\"OK\"}";

        ReponseSiryf reponse = ReponseSiryf.lire(corps);

        assertFalse(reponse.sansErreur());
        assertEquals(2, reponse.erreurs().size());
        assertEquals("ligne 4 : Code M.I.B.207 est introuvable", reponse.erreurs().get(0));
        assertTrue(reponse.resume().startsWith("2 lignes refusées par les contrôles BCRG"),
                reponse.resume());
    }

    /** Sur un refus, SIRYF renvoie le tableau d'erreurs à la racine (constaté sur FINS_09). */
    @Test
    void lit_aussi_un_tableau_d_erreurs_a_la_racine() {
        String corps = "[{\"version\":0,\"id\":276,\"ligne\":\"0\","
                + "\"erreur\":\" Code FINS_09 est introuvable \",\"ecart\":0.0}]";

        ReponseSiryf reponse = ReponseSiryf.lire(corps);

        assertEquals(1, reponse.erreurs().size());
        assertEquals("ligne 0 : Code FINS_09 est introuvable", reponse.erreurs().get(0));
    }

    @Test
    void un_corps_en_texte_brut_ne_signale_rien() {
        assertTrue(ReponseSiryf.lire("Traitement effectué avec succès").sansErreur());
    }

    @Test
    void un_corps_vide_ou_absent_ne_signale_rien() {
        assertTrue(ReponseSiryf.lire(null).sansErreur());
        assertTrue(ReponseSiryf.lire("").sansErreur());
        assertTrue(ReponseSiryf.lire("   ").sansErreur());
    }

    /** Un format inattendu ne doit pas faire échouer une transmission qui a réussi. */
    @Test
    void un_json_inattendu_ne_signale_rien() {
        assertTrue(ReponseSiryf.lire("{\"erreurs\":\"aucune\"}").sansErreur());
        assertTrue(ReponseSiryf.lire("{\"autre\":123}").sansErreur());
        assertTrue(ReponseSiryf.lire("{ ceci n'est pas du json").sansErreur());
    }

    /**
     * Le corps exact reçu le 28/08/2026 : la remise est refusée, et SIRYF le dit dans son
     * {@code statutCode} tout en répondant <strong>HTTP 200</strong>. Sans cette lecture, la feuille
     * était annoncée transmise et son idempotence gravée — elle n'aurait plus jamais été rejouée.
     */
    @Test
    void un_200_au_statut_conflict_est_un_refus_de_la_remise() {
        String corps = "{\"erreurs\":[],\"description\":\" Cette date d'arreté est deja utilisée "
                + "pour cette institution FASeF-G.\\nAdressez vous à la BCRG pour avoir une "
                + "autorisation de Modification\",\"statutCode\":\"CONFLICT\"}";

        ReponseSiryf reponse = ReponseSiryf.lire(corps);

        assertTrue(reponse.refusee(), "un statutCode autre que OK est un refus, quel que soit le HTTP");
        assertTrue(reponse.refus().startsWith("Cette date d'arreté est deja utilisée"), reponse.refus());
        assertFalse(reponse.refus().contains("\n"), "le motif tient sur une ligne : il part en audit");
        assertTrue(reponse.sansErreur(), "aucune ligne rejetée : c'est la remise entière qui l'est");
    }

    @Test
    void un_statut_ok_n_est_pas_un_refus() {
        assertFalse(ReponseSiryf.lire("{\"erreurs\":[],\"statutCode\":\"OK\"}").refusee());
    }

    /** Un corps sans statutCode ne dit rien du sort de la remise : on ne lui fait pas dire un refus. */
    @Test
    void un_corps_muet_sur_le_statut_n_est_pas_un_refus() {
        assertFalse(ReponseSiryf.lire("Traitement effectué avec succès").refusee());
        assertFalse(ReponseSiryf.lire("{\"erreurs\":[]}").refusee());
        assertFalse(ReponseSiryf.lire(null).refusee());
    }

    /** Un refus sans description reste identifiable par son statut. */
    @Test
    void un_refus_sans_description_nomme_au_moins_son_statut() {
        assertEquals("refus SIRYF (CONFLIT)",
                ReponseSiryf.lire("{\"statutCode\":\"CONFLIT\"}").refus());
    }

    /** Au-delà de cinq, le résumé compte au lieu d'énumérer : il finit dans un compte rendu lu. */
    @Test
    void le_resume_reste_lisible_quand_les_erreurs_sont_nombreuses() {
        StringBuilder corps = new StringBuilder("{\"erreurs\":[");
        for (int i = 1; i <= 12; i++) {
            corps.append(i > 1 ? "," : "")
                    .append("{\"ligne\":\"").append(i).append("\",\"erreur\":\"Code inconnu\"}");
        }
        corps.append("]}");

        ReponseSiryf reponse = ReponseSiryf.lire(corps.toString());

        assertEquals(12, reponse.erreurs().size(), "toutes les erreurs restent disponibles");
        assertTrue(reponse.resume().contains("et 7 autre(s)"), reponse.resume());
    }
}
