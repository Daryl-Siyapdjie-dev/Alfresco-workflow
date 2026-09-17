package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.audit.AuditContext;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.client.dto.TransmissionImf.StatutEnum;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationService;
import com.kimia.bcrg_integration.pipeline.DossierReport;
import com.kimia.bcrg_integration.pipeline.JobRegistries;
import com.kimia.bcrg_integration.pipeline.PeriodeResolver;
import com.kimia.bcrg_integration.pipeline.TransmissionFactory;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Endpoint interne de déclenchement du pipeline (Lot 8, ticket 8.1) : le workflow documentaire
 * (Alfresco) appelle cette API quand un dossier est prêt, et reçoit le {@link DossierReport}.
 * <p>Deux modes d'entrée, pour les deux façons dont un classeur peut arriver :
 * <ul>
 *   <li><strong>par chemin</strong> — le fichier est déjà accessible au serveur (partage monté) ;</li>
 *   <li><strong>par dépôt</strong> — le classeur est poussé dans la requête (multipart), traité puis
 *       supprimé ; rien n'est conservé sur disque.</li>
 * </ul>
 * L'accès est restreint par {@link InternalApiFilter} (ticket 8.2) : tout est sous
 * {@code /api/internal/}.
 */
@RestController
@RequestMapping("/api/internal/integration")
public class IntegrationController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);

    private final DossierIntegrationService dossiers;
    private final TransmissionFactory transmissions;
    private final JobRegistries registres;
    private final PeriodeResolver periodes;
    private final com.kimia.bcrg_integration.pipeline.ApercuSiryfService apercus;

    public IntegrationController(DossierIntegrationService dossiers, TransmissionFactory transmissions,
                                 JobRegistries registres, PeriodeResolver periodes,
                                 com.kimia.bcrg_integration.pipeline.ApercuSiryfService apercus) {
        this.dossiers = dossiers;
        this.transmissions = transmissions;
        this.registres = registres;
        this.periodes = periodes;
        this.apercus = apercus;
    }

    /**
     * Intègre un classeur déjà présent sur le serveur.
     * <p>Deux chemins pour la même opération : {@code /dossier} dit ce que fait l'endpoint depuis
     * qu'il traite plusieurs fichiers réglementaires (le fichier est choisi par {@code codeFichier}),
     * {@code /sigimf} est conservé parce que le workflow Alfresco et les outils de recette l'appellent.
     */
    @PostMapping({"/dossier", "/sigimf"})
    public DossierReport integrer(@Valid @RequestBody IntegrationRequest requete) {
        File classeur = new File(requete.fichier());
        if (!classeur.isFile()) {
            throw new IllegalArgumentException("Classeur introuvable sur le serveur : " + requete.fichier());
        }
        TransmissionImf transmission = transmission(classeur, requete.annee(), requete.mois(),
                requete.statutOuCreation());

        log.info("Intégration demandée : fichier={}, feuille={}, fichier réglementaire={}, arrêté={}, acteur={}",
                requete.fichier(), requete.feuilleOuToutes(), requete.codeFichierOuDefaut(),
                transmission.getDateArrete(), requete.acteur());
        return AuditContext.avecActeur(requete.acteur(),
                () -> dossiers.run(classeur, requete.codeFichierOuDefaut(), transmission,
                        List.of(requete.feuilleOuToutes())));
    }

    /** Intègre un classeur poussé dans la requête ; le fichier temporaire est supprimé après traitement. */
    @PostMapping(path = {"/dossier/upload", "/sigimf/upload"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DossierReport integrerDepot(
            @RequestPart("fichier") MultipartFile depot,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(defaultValue = "TOUS") String feuille,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = DossierIntegrationService.CODE_FICHIER_AUTO) String codeFichier,
            @RequestParam(required = false) String acteur)
            throws IOException {

        if (depot.isEmpty()) {
            throw new IllegalArgumentException("Le fichier déposé est vide");
        }
        StatutEnum statutEnum = (statut == null || statut.isBlank())
                ? StatutEnum.CREATION
                : StatutEnum.fromValue(statut.trim().toUpperCase(java.util.Locale.ROOT));

        Path temporaire = Files.createTempFile("bcrg-depot-", ".xlsx");
        try {
            depot.transferTo(temporaire);
            TransmissionImf transmission = transmission(temporaire.toFile(), annee, mois, statutEnum);
            log.info("Intégration d'un dépôt : {} ({} octets), feuille={}, fichier réglementaire={}, "
                            + "arrêté={}, acteur={}",
                    depot.getOriginalFilename(), depot.getSize(), feuille, codeFichier,
                    transmission.getDateArrete(), acteur);
            return AuditContext.avecActeur(acteur,
                    () -> dossiers.run(temporaire.toFile(), codeFichier, transmission, List.of(feuille)));
        } finally {
            Files.deleteIfExists(temporaire);
        }
    }

    /**
     * En-tête de transmission de la demande : période explicite si elle est fournie, sinon
     * <strong>déduite du classeur</strong> (« Date des rapports »).
     * <p>Le workflow documentaire ne saisit aucune période — c'est l'approbation du transmetteur qui
     * déclenche l'envoi. Une demande manuelle, elle, garde la main : {@code annee} + {@code mois}
     * l'emportent toujours. Fournir l'un sans l'autre est une demande incohérente, donc refusée.
     */
    private TransmissionImf transmission(File classeur, Integer annee, Integer mois, StatutEnum statut) {
        if (annee != null && mois != null) {
            return transmissions.forMonth(annee, mois, statut);
        }
        if (annee != null || mois != null) {
            throw new IllegalArgumentException(
                    "Période incomplète : fournir « annee » et « mois » ensemble, ou aucun des deux "
                            + "(la date d'arrêté est alors lue dans le classeur).");
        }
        return transmissions.forDateArrete(periodes.dateArrete(classeur), statut);
    }

    /**
     * Feuilles prises en charge pour un fichier réglementaire — permet à l'appelant de savoir ce qui
     * sera traité. Sans paramètre : les feuilles de SIGIMF, le fichier par défaut de l'API.
     */
    @GetMapping("/feuilles")
    public Set<String> feuillesCouvertes(
            @RequestParam(defaultValue = DossierIntegrationService.CODE_FICHIER_SIGIMF) String codeFichier) {
        return registres.forFile(codeFichier).sheets();
    }

    /** Codes des fichiers réglementaires pris en charge (« SIGIMF », « SITU », …). */
    @GetMapping("/fichiers")
    public Set<String> fichiersCouverts() {
        return registres.codesFichiers();
    }

    /**
     * Aperçu de ce que SIRYF détient réellement pour un fichier à une date d'arrêté : renvoie le
     * classeur Excel relu chez SIRYF (contre-preuve d'une transmission). Utilisé par le workflow
     * documentaire pour déposer un aperçu dans « Mes fichiers » du transmetteur après l'envoi.
     * @param codeFichier ex. SIGIMF, SITU, PRUD, IGEC, FINS
     * @param dateArrete  fin de période, format aaaa-mm-jj
     */
    @GetMapping("/dossier/apercu")
    public org.springframework.http.ResponseEntity<byte[]> apercu(
            @RequestParam String codeFichier,
            @RequestParam String dateArrete) {
        byte[] classeur = apercus.apercu(codeFichier, java.time.LocalDate.parse(dateArrete.trim()));
        String nom = "apercu-siryf-" + codeFichier.toUpperCase() + "-" + dateArrete.trim() + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=\"" + nom + "\"")
                .body(classeur);
    }
}
