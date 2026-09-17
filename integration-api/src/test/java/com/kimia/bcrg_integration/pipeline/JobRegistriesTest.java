package com.kimia.bcrg_integration.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide la résolution « code fichier → registre ». C'est le point d'entrée qui rend le pipeline
 * indépendant du fichier réglementaire traité : une erreur ici enverrait un classeur SITU sur les
 * endpoints SIGIMF.
 */
class JobRegistriesTest {

    private final SigimfJobRegistry sigimf = new SigimfJobRegistry((u, b) -> ResponseEntity.ok("x"));
    private final SituJobRegistry situ = new SituJobRegistry((u, b) -> ResponseEntity.ok("x"));

    @Test
    void resout_chaque_fichier_sur_son_registre() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        assertSame(sigimf, registres.forFile("SIGIMF"));
        assertSame(situ, registres.forFile("SITU"));
        assertEquals(List.of("SIGIMF", "SITU"), List.copyOf(registres.codesFichiers()));
    }

    @Test
    void tolere_la_casse_et_les_espaces_de_la_demande() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        assertSame(situ, registres.forFile(" situ "));
        assertSame(sigimf, registres.forFile("SigImf"));
    }

    @Test
    void refuse_un_code_inconnu_en_listant_les_codes_connus() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        // erreur de demande (→ 400), pas une panne : l'appelant doit voir ce qu'il pouvait demander
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> registres.forFile("PRUD"));
        assertTrue(e.getMessage().contains("SIGIMF") && e.getMessage().contains("SITU"),
                () -> "message peu utile : " + e.getMessage());
    }

    /**
     * La règle Alfresco qui déclenche l'envoi ne sait pas quel fichier réglementaire le validateur
     * vient d'approuver : c'est le classeur qui doit le dire, par ses feuilles. Une détection fausse
     * enverrait un SITU sur les endpoints SIGIMF.
     */
    @Test
    void detecte_le_fichier_reglementaire_depuis_les_feuilles_du_classeur() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        assertSame(situ, registres.detecte(List.of("Liste-Situ-Annexes", "SITU_01", "SITU_02", "SITU_03")));
        assertSame(sigimf, registres.detecte(List.of("M.0.INFO.G", "M.I.BILAN", "M.III.HS_BILAN")));
    }

    @Test
    void detecte_encore_un_classeur_ampute_de_feuilles() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        // un seul onglet connu suffit : « TOUS » traite ce qui est présent, pas une liste exigée
        assertSame(situ, registres.detecte(List.of("Mode d'emploi", "SITU_02")));
    }

    @Test
    void refuse_un_classeur_hors_perimetre() {
        JobRegistries registres = new JobRegistries(sigimf, situ);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registres.detecte(List.of("Feuil1", "Budget 2026")));
        assertTrue(e.getMessage().contains("SIGIMF") && e.getMessage().contains("SITU"),
                () -> "message peu utile : " + e.getMessage());
    }

    @Test
    void refuse_deux_registres_pour_le_meme_fichier() {
        // erreur de câblage : elle doit sauter au démarrage, pas produire un envoi silencieusement perdu
        assertThrows(IllegalStateException.class,
                () -> new JobRegistries(sigimf, new SigimfJobRegistry((u, b) -> ResponseEntity.ok("x"))));
    }
}
