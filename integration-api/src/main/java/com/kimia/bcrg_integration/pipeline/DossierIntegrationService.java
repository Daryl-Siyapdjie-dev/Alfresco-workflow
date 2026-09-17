package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.audit.AuditAction;
import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.Auditor;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Traitement d'un <strong>dossier</strong> complet : ouvre un classeur, intègre les feuilles
 * demandées via {@link RetryingIntegrationService} et rend un {@link DossierReport}.
 * <p>C'est le point d'entrée unique du pipeline au niveau fichier — partagé par le runner de smoke
 * test et par l'endpoint interne (Lot 8) : une seule implémentation de la boucle, donc un seul
 * comportement à tester et à faire évoluer.
 * <p>Une feuille en erreur <strong>n'interrompt pas</strong> le dossier : son issue est collectée et
 * le traitement continue (la reprise se fait feuille par feuille, §7). Seule une erreur d'ouverture
 * du classeur interrompt tout, par {@link DossierIntegrationException}.
 */
@Service
public class DossierIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(DossierIntegrationService.class);

    /** Code du fichier réglementaire par défaut de l'API interne. */
    public static final String CODE_FICHIER_SIGIMF = SigimfJobRegistry.CODE_FICHIER;

    /**
     * Code demandant la <strong>détection</strong> du fichier réglementaire d'après les feuilles du
     * classeur — défaut de l'API interne, car la règle Alfresco qui déclenche l'envoi ne sait pas
     * quel fichier le validateur vient d'approuver.
     */
    public static final String CODE_FICHIER_AUTO = "AUTO";

    /** Valeurs acceptées pour « toutes les feuilles couvertes ». */
    private static final Collection<String> TOUTES = List.of("TOUS", "TOUTES", "ALL");

    private final JobRegistries registres;
    private final RetryingIntegrationService resilient;
    private final Auditor auditor;

    @Autowired
    public DossierIntegrationService(JobRegistries registres, RetryingIntegrationService resilient,
                                     Auditor auditor) {
        this.registres = registres;
        this.resilient = resilient;
        this.auditor = auditor;
    }

    /** Variante sans audit : pour un usage hors contexte Spring (tests, outils en ligne de commande). */
    public DossierIntegrationService(JobRegistries registres, RetryingIntegrationService resilient) {
        this(registres, resilient, Auditor.noop());
    }


    /**
     * Intègre un classeur.
     *
     * @param fichier      classeur à traiter
     * @param codeFichier  code du fichier réglementaire (« SIGIMF », « SITU », …) : choisit le registre
     *                     à appliquer, et sert de clé d'idempotence et d'audit
     * @param transmission en-tête de transmission (période + statut)
     * @param feuilles     feuilles à traiter ; {@code null}, vide ou « TOUS » = toutes les feuilles
     *                     couvertes par le registre et présentes dans le classeur
     */
    public DossierReport run(File fichier, String codeFichier, TransmissionImf transmission,
                             Collection<String> feuilles) {
        boolean toutes = estToutesFeuilles(feuilles);
        long debut = System.nanoTime();
        List<IntegrationResult> resultats = new ArrayList<>();

        // Ouverture d'abord : le registre peut dépendre du contenu (codeFichier = AUTO), et un
        // classeur illisible doit rester un « classeur illisible », pas un « fichier inconnu ».
        Workbook workbook = ouvrir(fichier, codeFichier, transmission, debut);
        try (workbook) {
            JobRegistry registry = resoudreRegistre(codeFichier, workbook);
            String code = registry.codeFichier();
            AuditTarget dossier = AuditTarget.dossier(code, transmission.getDateArrete());
            List<String> cibles = toutes ? feuillesCouvertes(workbook, registry) : new ArrayList<>(feuilles);
            auditor.succes(AuditAction.DEPOT, dossier,
                    "classeur « " + fichier.getName() + " » ouvert — " + cibles.size() + " feuille(s) demandée(s)",
                    ecoule(debut));

            for (String feuille : cibles) {
                SheetJob<?, ?> job = registry.forSheet(feuille);
                if (job == null) {
                    log.warn("  {} : feuille non prise en charge par le registre — ignorée", feuille);
                    resultats.add(IntegrationResult.erreurDonnees(feuille, 0,
                            "feuille non prise en charge par le registre " + code));
                    continue;
                }
                if (workbook.getSheet(feuille) == null) {
                    if (toutes) {
                        log.info("  {} : absente du classeur — ignorée", feuille);
                        continue;   // « TOUS » = ce qui est présent, pas une exigence d'exhaustivité
                    }
                    resultats.add(IntegrationResult.erreurDonnees(feuille, 0, "feuille absente du classeur"));
                    continue;
                }
                Periodicite rythme = registry.periodicite(feuille);
                java.time.LocalDate arrete = transmission.getDateArrete().toLocalDate();
                if (!rythme.accepte(arrete)) {
                    // ne pas appeler la BCRG pour se faire refuser : un 5xx serait même rejoué 4 fois
                    log.info("  {} : {} — {} n'est pas une fin {}", feuille, rythme, arrete,
                            rythme.periodeLisible());
                    resultats.add(IntegrationResult.horsPeriode(feuille, rythme, arrete));
                    continue;
                }
                resultats.add(resilient.runResilient(workbook, job, transmission, code));
            }

            DossierReport rapport = DossierReport.of(fichier.getName(), code,
                    transmission.getDateArrete(), resultats);
            auditor.etape(AuditAction.CLOTURE, dossier,
                    rapport.isComplet() ? Resultat.SUCCES : Resultat.ECHEC,
                    null, rapport.total() + " feuille(s) traitée(s) — " + rapport.bilan(), ecoule(debut));
            log.info("Dossier {} ({}) : {}", fichier.getName(), code, rapport.bilan());
            return rapport;

        } catch (DossierIntegrationException | IllegalArgumentException e) {
            throw e;                                   // demande fautive : ne pas la déguiser en panne
        } catch (Exception e) {
            auditor.echec(AuditAction.DEPOT, AuditTarget.dossier(etiquette(codeFichier),
                    transmission.getDateArrete()), null, e.getMessage(), ecoule(debut));
            throw new DossierIntegrationException(
                    "Traitement du classeur impossible : " + fichier.getName() + " — " + e.getMessage(), e);
        }
    }

    /** Ouvre le classeur ; un échec ici est définitif pour le dossier entier (rien n'a pu être lu). */
    private Workbook ouvrir(File fichier, String codeFichier, TransmissionImf transmission, long debut) {
        try {
            return WorkbookFactory.create(fichier, null, true);
        } catch (Exception e) {
            auditor.echec(AuditAction.DEPOT, AuditTarget.dossier(etiquette(codeFichier),
                    transmission.getDateArrete()), null, e.getMessage(), ecoule(debut));
            throw new DossierIntegrationException(
                    "Classeur illisible : " + fichier.getName() + " — " + e.getMessage(), e);
        }
    }

    /**
     * Registre à appliquer : celui demandé, ou celui que trahissent les feuilles du classeur quand
     * l'appelant n'a rien précisé (workflow Alfresco).
     */
    private JobRegistry resoudreRegistre(String codeFichier, Workbook workbook) {
        if (estAuto(codeFichier)) {
            JobRegistry detecte = registres.detecte(nomsDeFeuilles(workbook));
            log.info("Fichier réglementaire détecté d'après les feuilles du classeur : {}",
                    detecte.codeFichier());
            return detecte;
        }
        return registres.forFile(codeFichier);         // code inconnu → erreur de demande (400)
    }

    private static boolean estAuto(String codeFichier) {
        return codeFichier == null || codeFichier.isBlank()
                || CODE_FICHIER_AUTO.equalsIgnoreCase(codeFichier.trim());
    }

    private static String etiquette(String codeFichier) {
        return estAuto(codeFichier) ? CODE_FICHIER_AUTO : codeFichier;
    }

    /**
     * Feuilles visées par «&nbsp;TOUS&nbsp;» : celles <strong>du classeur</strong> que le registre sait
     * envoyer.
     * <p>Partir du classeur plutôt que de la liste déclarée par le registre, c'est la même chose pour
     * les cinq fichiers dont les onglets portent un nom fixe — mais pas pour BALG, dont l'onglet
     * porte le millésime de l'exercice (« BALANCE H.IMF2026 »). Un nom déclaré en dur cesserait de
     * correspondre l'année suivante, et « TOUS » sauterait la balance sans rien dire.
     */
    private static List<String> feuillesCouvertes(Workbook workbook, JobRegistry registry) {
        List<String> couvertes = new ArrayList<>();
        for (String feuille : nomsDeFeuilles(workbook)) {
            if (registry.forSheet(feuille) != null) {
                couvertes.add(feuille);
            }
        }
        return couvertes;
    }

    private static List<String> nomsDeFeuilles(Workbook workbook) {
        List<String> noms = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            noms.add(workbook.getSheetName(i));
        }
        return noms;
    }

    private static boolean estToutesFeuilles(Collection<String> feuilles) {
        if (feuilles == null || feuilles.isEmpty()) {
            return true;
        }
        return feuilles.stream()
                .anyMatch(f -> f != null && TOUTES.contains(f.trim().toUpperCase(Locale.ROOT)));
    }

    private static long ecoule(long departNanos) {
        return (System.nanoTime() - departNanos) / 1_000_000;
    }
}
