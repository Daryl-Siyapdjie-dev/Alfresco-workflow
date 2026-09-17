package com.kimia.bcrg_integration.audit;

import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Point d'entrée unique de l'audit pour le pipeline (ticket 7.2) : il horodate, attribue l'acteur,
 * <strong>masque les secrets</strong> ({@link AuditRedactor}) puis dépose l'événement dans la
 * {@link AuditTrail} — et archive les accusés de réception dans l'{@link AcknowledgementStore}.
 * <p>Ne connaît rien du pipeline (pas de dépendance vers {@code pipeline}) : c'est l'appelant qui
 * traduit son issue en {@link AuditAction} + {@link Resultat}. L'audit reste ainsi réutilisable pour
 * la chaîne de transmission comme pour l'import.
 */
@Component
public class Auditor {

    private static final Logger log = LoggerFactory.getLogger(Auditor.class);

    private final AuditTrail trail;
    private final AcknowledgementStore acknowledgements;
    private final String acteurParDefaut;

    public Auditor(AuditTrail trail, AcknowledgementStore acknowledgements,
                   @Value("${bcrg.audit.acteur:bcrg-integration}") String acteurParDefaut) {
        this.trail = trail;
        this.acknowledgements = acknowledgements;
        this.acteurParDefaut = acteurParDefaut;
    }

    /** Auditeur inerte, pour les composants instanciés hors contexte Spring (tests, outils). */
    public static Auditor noop() {
        return new Auditor(AuditTrail.NOOP, AcknowledgementStore.NOOP, "-");
    }

    /** Journalise une étape du parcours et renvoie l'événement déposé. */
    public AuditEvent etape(AuditAction action, AuditTarget cible, Resultat resultat,
                            Integer httpStatus, String detail, long dureeMillis) {
        AuditEvent evenement = new AuditEvent(Instant.now(), acteur(), action, cible, resultat,
                httpStatus, AuditRedactor.mask(detail), dureeMillis);
        trail.append(evenement);
        if (resultat == Resultat.ECHEC) {
            log.warn("AUDIT {} {} {} — HTTP {} — {}", action, resultat, cibleLisible(cible),
                    httpStatus, evenement.detail());
        } else {
            log.info("AUDIT {} {} {} ({} ms)", action, resultat, cibleLisible(cible), dureeMillis);
        }
        return evenement;
    }

    /** Raccourci : étape réussie sans appel HTTP. */
    public AuditEvent succes(AuditAction action, AuditTarget cible, String detail, long dureeMillis) {
        return etape(action, cible, Resultat.SUCCES, null, detail, dureeMillis);
    }

    /** Raccourci : étape en échec. */
    public AuditEvent echec(AuditAction action, AuditTarget cible, Integer httpStatus,
                            String detail, long dureeMillis) {
        return etape(action, cible, Resultat.ECHEC, httpStatus, detail, dureeMillis);
    }

    /**
     * Archive l'accusé de réception d'un envoi accepté (ticket 7.3) — le corps est masqué avant
     * conservation, au même titre que les détails d'événements.
     */
    public void accuse(AuditTarget cible, int httpStatus, String corps) {
        acknowledgements.store(new Acknowledgement(cible, Instant.now(), httpStatus,
                AuditRedactor.mask(corps)));
    }

    /**
     * Acteur inscrit dans l'événement : celui du traitement en cours quand l'appelant l'a précisé
     * (le transmetteur du workflow SIRYF, via {@link AuditContext}), sinon le compte de service du module.
     */
    private String acteur() {
        return AuditContext.acteurCourant().orElse(acteurParDefaut);
    }

    public AuditTrail trail() {
        return trail;
    }

    public AcknowledgementStore acknowledgements() {
        return acknowledgements;
    }

    private static String cibleLisible(AuditTarget cible) {
        if (cible == null) {
            return "-";
        }
        String feuille = cible.feuille() != null ? cible.feuille() : "(dossier)";
        return cible.codeFichier() + "/" + feuille + "@" + cible.dateArrete();
    }
}
