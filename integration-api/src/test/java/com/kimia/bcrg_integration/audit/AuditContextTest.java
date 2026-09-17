package com.kimia.bcrg_integration.audit;

import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §9 — la piste d'audit doit dire <strong>qui</strong> a déclenché un envoi. Le module transmet sous
 * un compte de service unique côté SIRYF, mais dans le workflow Alfresco c'est l'approbation d'un
 * <em>validateur</em> qui lance le pipeline : sans cette attribution, la trace réglementaire ne
 * distinguerait pas deux envois validés par deux personnes différentes.
 */
class AuditContextTest {

    private final InMemoryAuditTrail trail = new InMemoryAuditTrail(10);
    private final Auditor auditor = new Auditor(trail, AcknowledgementStore.NOOP, "bcrg-integration");
    private final AuditTarget cible = AuditTarget.dossier("SIGIMF", OffsetDateTime.now());

    @Test
    void attribue_l_evenement_au_validateur_du_traitement_en_cours() {
        AuditContext.avecActeur("validateur.diallo",
                () -> auditor.succes(AuditAction.DEPOT, cible, "dépôt validé", 0));

        assertEquals("validateur.diallo", trail.events().get(0).acteur());
    }

    @Test
    void revient_au_compte_de_service_apres_le_traitement() {
        AuditContext.avecActeur("validateur.diallo",
                () -> auditor.succes(AuditAction.DEPOT, cible, "dépôt validé", 0));
        auditor.etape(AuditAction.CLOTURE, cible, Resultat.SUCCES, null, "hors requête", 0);

        assertEquals("bcrg-integration", trail.events().get(1).acteur(),
                "la fuite d'un acteur d'une requête sur la suivante fausserait la trace");
    }

    @Test
    void sans_validateur_l_acteur_reste_le_compte_de_service() {
        AuditContext.avecActeur("  ", () -> auditor.succes(AuditAction.DEPOT, cible, "dépôt", 0));

        assertEquals("bcrg-integration", trail.events().get(0).acteur());
    }
}
