package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.audit.AuditAction;
import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.Auditor;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Enveloppe résiliente de l'{@link IntegrationService} (Lot 6, §7) :
 * <ul>
 *   <li><strong>Idempotence</strong> : si la feuille a déjà été transmise avec succès pour la même
 *       {@link IdempotencyKey}, on n'envoie pas de nouveau (pas de doublon) ;</li>
 *   <li><strong>Rejeu</strong> : sur erreur <em>technique/transitoire</em> (timeout → {@code ERREUR_TECHNIQUE},
 *       ou API 5xx), on retente selon {@link RetryPolicy} (backoff exponentiel). Les erreurs
 *       <em>fonctionnelles</em> (données, API 4xx) ne sont <strong>pas</strong> rejouées ;</li>
 *   <li><strong>Un 500 n'a droit qu'à une seconde chance</strong> : il dit que le serveur a échoué
 *       <em>sur ce contenu</em>, non qu'il était indisponible. Insister quatre fois ne fait que
 *       retarder le compte rendu — voir {@link #tentativesAccordees}.</li>
 * </ul>
 * La reprise se fait au niveau feuille : c'est toute la feuille qui est relue/renvoyée.
 * <p>Les deux décisions — envoi ignoré, nouvelle tentative — sont tracées dans la piste d'audit
 * (Lot 7, ticket 7.2) : c'est ce qui explique après coup pourquoi une feuille n'a pas été renvoyée.
 */
@Service
public class RetryingIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(RetryingIntegrationService.class);

    private final IntegrationService integration;
    private final RetryPolicy policy;
    private final TransmissionLedger ledger;
    private final Sleeper sleeper;
    private final Auditor auditor;

    @Autowired
    public RetryingIntegrationService(IntegrationService integration, RetryPolicy policy,
                                      TransmissionLedger ledger, Sleeper sleeper, Auditor auditor) {
        this.integration = integration;
        this.policy = policy;
        this.ledger = ledger;
        this.sleeper = sleeper;
        this.auditor = auditor;
    }

    /** Variante sans audit : pour un usage hors contexte Spring (tests, outils en ligne de commande). */
    public RetryingIntegrationService(IntegrationService integration, RetryPolicy policy,
                                      TransmissionLedger ledger, Sleeper sleeper) {
        this(integration, policy, ledger, sleeper, Auditor.noop());
    }

    public <I, D> IntegrationResult runResilient(Workbook workbook, SheetJob<I, D> job,
                                                 TransmissionImf transmission, String codeFichier) {
        IdempotencyKey key = new IdempotencyKey(codeFichier, job.sheetName(), transmission.getDateArrete());
        AuditTarget cible = new AuditTarget(codeFichier, job.sheetName(), transmission.getDateArrete());

        if (ledger.isAlreadySucceeded(key)) {
            log.info("{} déjà transmise (dateArrete={}) — envoi ignoré (idempotence)",
                    job.sheetName(), transmission.getDateArrete());
            auditor.etape(AuditAction.IDEMPOTENCE, cible, Resultat.IGNORE, null,
                    "déjà transmise pour cette période — envoi ignoré", 0);
            return IntegrationResult.dejaTransmis(job.sheetName());
        }

        IntegrationResult result = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            result = integration.run(workbook, job, transmission, codeFichier);

            if (result.estTransmis()) {
                // « transmis », et non « réussi » : une transmission acceptée avec des lignes
                // refusées a consommé sa période tout autant. La rejouer se ferait refuser.
                ledger.recordSuccess(key);
                return result;
            }
            int tentativesAccordees = tentativesAccordees(result, policy.maxAttempts());
            if (!isRetryable(result) || attempt >= tentativesAccordees) {
                if (isRetryable(result) && tentativesAccordees < policy.maxAttempts()) {
                    log.warn("{} : HTTP 500 après {} tentative(s) — rejeu abandonné, le serveur "
                            + "échoue sur ce contenu et non par accident", job.sheetName(), attempt);
                    auditor.etape(AuditAction.REPRISE, cible, Resultat.ECHEC, result.httpStatus(),
                            "500 reproductible — rejeu abandonné après " + attempt + " tentative(s)", 0);
                }
                return result;   // erreur fonctionnelle, ou plus de tentatives
            }


            long backoff = policy.backoffMillis(attempt);
            log.warn("{} : {} (tentative {}/{}) — nouveau rejeu dans {} ms",
                    job.sheetName(), result.outcome(), attempt, policy.maxAttempts(), backoff);
            auditor.etape(AuditAction.REPRISE, cible, Resultat.ECHEC, result.httpStatus(),
                    result.outcome() + " — tentative " + attempt + "/" + policy.maxAttempts()
                            + ", rejeu dans " + backoff + " ms", 0);
            try {
                sleeper.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result;
            }
        }
        return result;
    }

    /**
     * Nombre de tentatives réellement accordées à cette issue.
     *
     * <p>Un <strong>{@code 500 Internal Server Error} n'est pas un incident de transport, c'est un
     * défaut de traitement</strong> : le serveur a reçu le contenu, l'a compris, et a échoué dessus.
     * Le même contenu produira le même échec. On lui accorde <em>une</em> seconde chance — un
     * hoquet reste possible — mais pas quatre.
     *
     * <p>Ce que cela évite : sur une feuille que le serveur refuse systématiquement, la politique
     * complète dépense quatre appels pour obtenir quatre fois la même réponse, et retarde d'autant
     * le compte rendu.
     *
     * <p>Les autres 5xx gardent la politique complète : {@code 502}, {@code 503} et {@code 504}
     * disent une indisponibilité passagère de l'infrastructure, pas un refus du contenu — tout
     * comme les incidents réseau ({@code ERREUR_TECHNIQUE}).
     */
    static int tentativesAccordees(IntegrationResult r, int maxAttempts) {
        boolean defautDeTraitement = r.outcome() == Outcome.ERREUR_API
                && r.httpStatus() != null && r.httpStatus() == 500;
        return defautDeTraitement ? Math.min(2, maxAttempts) : maxAttempts;
    }

    /** Rejouable = erreur technique/transitoire : incident inattendu (timeout) ou API 5xx. */
    static boolean isRetryable(IntegrationResult r) {
        if (r.outcome() == Outcome.ERREUR_TECHNIQUE) {
            return true;
        }
        return r.outcome() == Outcome.ERREUR_API && r.httpStatus() != null && r.httpStatus() >= 500;
    }
}
