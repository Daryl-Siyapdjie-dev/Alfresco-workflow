package com.kimia.bcrg_integration.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ticket 7.4 : aucune donnée confidentielle ne doit atteindre la piste d'audit. On fige ici les
 * formes réellement rencontrées dans ce module — en-tête {@code Authorization} du client REST,
 * corps du {@code signin}, JWT rendu par l'API — et on vérifie qu'une réponse métier ordinaire
 * traverse le masquage <strong>intacte</strong> (sans quoi les accusés deviendraient illisibles).
 */
class AuditRedactorTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJpbWYifQ.s1gnatur3";

    @Test
    void masque_l_entete_authorization_en_entier() {
        assertEquals("Authorization: \"***\"",
                AuditRedactor.mask("Authorization: Bearer " + JWT));
    }

    @Test
    void masque_un_bearer_isole() {
        assertEquals("envoi refusé (Bearer ***)",
                AuditRedactor.mask("envoi refusé (Bearer " + JWT + ")"));
    }

    @Test
    void masque_mot_de_passe_et_jeton_dans_un_corps_json() {
        assertEquals("{\"username\":\"imf\",\"password\":\"***\"}",
                AuditRedactor.mask("{\"username\":\"imf\",\"password\":\"gu1n33\"}"));
        assertEquals("{\"access_token\":\"***\",\"expiration\":1787000000}",
                AuditRedactor.mask("{\"access_token\":\"" + JWT + "\",\"expiration\":1787000000}"));
    }

    @Test
    void masque_un_jwt_nu() {
        assertEquals("jeton obtenu : ***", AuditRedactor.mask("jeton obtenu : " + JWT));
    }

    @Test
    void laisse_intacte_une_reponse_metier() {
        String accuse = "{\"typeMessageRetour\":\"SUCCES\",\"nombreLignes\":9}";
        assertEquals(accuse, AuditRedactor.mask(accuse));
    }

    @Test
    void tolere_null_et_vide() {
        assertNull(AuditRedactor.mask(null));
        assertEquals("", AuditRedactor.mask(""));
    }
}
