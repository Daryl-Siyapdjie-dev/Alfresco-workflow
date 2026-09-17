package com.asf.guinee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Tâche automatique de transmission du workflow SIRYF ({@code serviceTaskTransmission}, étape 4 du
 * BPMN {@code siriyfValidationProcess}), déclenchée <strong>après l'approbation du groupe
 * {@code GROUP_TRANSMETTEURS}</strong>. Instanciée par Activiti via {@code activiti:class} (pas de
 * bean Spring visible depuis le BPMN dans cette configuration) : les services Alfresco sont obtenus
 * via {@code ContextLoader.getCurrentWebApplicationContext()} et l'écriture se fait en
 * {@code runAsSystem}.
 *
 * <p><strong>Ce qu'elle fait :</strong> pour chaque document du dossier (paquet {@code bpm_package}),
 * elle envoie le classeur au module d'intégration BCRG ({@code POST /api/internal/integration/dossier/upload}),
 * qui le transmet à SIRYF et rend un compte rendu JSON ({@code DossierReport}). Puis, dans le dossier
 * personnel du transmetteur (« Mes fichiers »), elle dépose :
 * <ul>
 *   <li>un <strong>aperçu Excel</strong> de ce que SIRYF détient (relu via {@code /dossier/apercu}) ;</li>
 *   <li>un <strong>bilan HTML lisible</strong> — « n feuilles transmises, m refusées + motifs » —
 *       qui porte un lien « plus de détails » vers l'aperçu.</li>
 * </ul>
 *
 * <p>On ne lève pas d'exception sur un échec de transmission ou de dépôt : la donnée d'audit vit dans
 * le statut du document et dans le bilan ; lever annulerait la transaction (donc l'écriture même).
 */
public class SiriyfTransmissionAction implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(SiriyfTransmissionAction.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String NS_SIRIYF = "http://www.asf.guinee.com/model/siriyf/1.0";
    private static final QName TYPE_DECLARATION = QName.createQName(NS_SIRIYF, "declaration");
    private static final QName PROP_STATUT = QName.createQName(NS_SIRIYF, "statut");
    private static final QName PROP_ANNEE = QName.createQName(NS_SIRIYF, "annee");
    private static final QName PROP_MOIS = QName.createQName(NS_SIRIYF, "mois");

    private static final String STATUT_SUCCES = "Archivé";
    private static final String STATUT_ECHEC = "Erreur de transmission";

    /** Issues (cf. IntegrationResult.Outcome) qui signent une transmission NON complète. */
    private static final List<String> MARQUEURS_ECHEC = List.of(
            "ERREUR_DONNEES", "ERREUR_API", "ERREUR_TECHNIQUE", "ACCEPTE_AVEC_ERREURS");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        LOG.info("=== SIRYF : transmission déclenchée après approbation du groupe transmetteur ===");

        ServiceRegistry services = (ServiceRegistry)
                ContextLoader.getCurrentWebApplicationContext().getBean("ServiceRegistry");
        final NodeService nodeService = services.getNodeService();
        final ContentService contentService = services.getContentService();

        final String url = config("siriyf.api.url", "SIRIYF_API_URL",
                "http://integration-api:8080/api/internal/integration/dossier/upload");
        final String jeton = config("siriyf.api.token", "SIRIYF_API_TOKEN", "");
        final String statutDemande = config("siriyf.api.statut", "SIRIYF_API_STATUT", "CREATION");
        final String transmetteur = transmetteur(execution);

        Object paquet = execution.getVariable("bpm_package");
        if (!(paquet instanceof ActivitiScriptNode)) {
            LOG.warn("Aucun paquet de documents (bpm_package) : rien à transmettre.");
            return;
        }
        final NodeRef paquetRef = ((ActivitiScriptNode) paquet).getNodeRef();

        final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build();

        AuthenticationUtil.runAsSystem(() -> {
            int envoyes = 0;
            for (ChildAssociationRef enfant : nodeService.getChildAssocs(paquetRef)) {
                NodeRef doc = enfant.getChildRef();
                ContentReader lecteur = contentService.getReader(doc, ContentModel.PROP_CONTENT);
                if (lecteur == null || !lecteur.exists()) {
                    continue;   // pas un fichier (dossier, lien…) : ignoré
                }
                envoyes++;
                String nom = String.valueOf(nodeService.getProperty(doc, ContentModel.PROP_NAME));
                Integer annee = toInt(nodeService.getProperty(doc, PROP_ANNEE));
                Integer mois = toInt(nodeService.getProperty(doc, PROP_MOIS));
                String compteRendu = envoyer(http, url, jeton, statutDemande, transmetteur, nom, lecteur, annee, mois);
                boolean succes = compteRendu != null && !contientEchec(compteRendu);
                marquerStatut(nodeService, doc, succes ? STATUT_SUCCES : STATUT_ECHEC, nom);

                if (compteRendu != null && transmetteur != null && !transmetteur.isBlank()) {
                    try {
                        deposerBilanEtApercu(services, http, url, jeton, transmetteur, nom, compteRendu);
                    } catch (Exception e) {
                        LOG.error("SIRYF : dépôt du bilan/aperçu pour « {} » impossible : {}", nom, e.toString());
                    }
                }
            }
            if (envoyes == 0) {
                LOG.warn("Le dossier ne contient aucun fichier à transmettre.");
            }
            LOG.info("=== SIRYF : transmission terminée ({} document(s) traité(s)) ===", envoyes);
            return null;
        });
    }

    /** Envoie un document au module et renvoie le compte rendu JSON (corps 2xx), ou {@code null}. */
    private String envoyer(HttpClient http, String url, String jeton, String statutDemande,
                           String transmetteur, String nom, ContentReader lecteur,
                           Integer annee, Integer mois) {
        try {
            byte[] octets = lire(lecteur);
            String frontiere = "----siriyf" + System.nanoTime();
            byte[] corps = corpsMultipart(frontiere, nom, octets, statutDemande, transmetteur, annee, mois);

            HttpRequest.Builder requete = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "multipart/form-data; boundary=" + frontiere)
                    .POST(BodyPublishers.ofByteArray(corps));
            if (jeton != null && !jeton.isBlank()) {
                requete.header("X-Internal-Token", jeton);
            }

            LOG.info("SIRYF : envoi de « {} » au module ({}) — statut={}, acteur={}, période={}",
                    nom, url, statutDemande, transmetteur,
                    (annee != null && mois != null) ? (annee + "-" + mois) : "(lue dans le classeur)");
            HttpResponse<String> reponse = http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
            int code = reponse.statusCode();
            String rc = reponse.body() == null ? "" : reponse.body();
            if (code >= 200 && code < 300) {
                LOG.info("SIRYF : « {} » traité par le module (HTTP {}). Réponse : {}", nom, code, tronquer(rc));
                return rc;
            }
            LOG.error("SIRYF : « {} » NON transmis (HTTP {}). Réponse : {}", nom, code, tronquer(rc));
            return null;
        } catch (Exception e) {
            LOG.error("SIRYF : échec d'appel du module pour « {} » : {}", nom, e.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dépôt du bilan + aperçu dans « Mes fichiers » du transmetteur
    // ---------------------------------------------------------------------------------------------

    private void deposerBilanEtApercu(ServiceRegistry services, HttpClient http, String urlUpload,
                                      String jeton, String transmetteur, String nomClasseur,
                                      String compteRenduJson) throws Exception {
        JsonNode report = JSON.readTree(compteRenduJson);
        String codeFichier = report.path("codeFichier").asText("");
        String dateArrete = report.path("dateArrete").asText("");     // ex. 2025-03-31T00:00:00Z
        String date = dateArrete.length() >= 10 ? dateArrete.substring(0, 10) : dateArrete;
        if (codeFichier.isBlank() || date.isBlank()) {
            LOG.warn("SIRYF : compte rendu sans codeFichier/dateArrete — dépôt ignoré.");
            return;
        }

        PersonService personService = services.getPersonService();
        FileFolderService fileFolderService = services.getFileFolderService();
        NodeService nodeService = services.getNodeService();
        ContentService contentService = services.getContentService();

        if (!personService.personExists(transmetteur)) {
            LOG.warn("SIRYF : transmetteur « {} » inconnu — pas de dépôt dans Mes fichiers.", transmetteur);
            return;
        }
        NodeRef personne = personService.getPerson(transmetteur);
        NodeRef home = (NodeRef) nodeService.getProperty(personne, ContentModel.PROP_HOMEFOLDER);
        if (home == null) {
            LOG.warn("SIRYF : « {} » n'a pas de dossier personnel — pas de dépôt.", transmetteur);
            return;
        }

        // Sous-dossier daté, sous « Mes fichiers ».
        String nomDossier = "Transmission " + codeFichier + " - " + date;
        NodeRef dossier = sousDossier(fileFolderService, home, nomDossier);

        // 1) Aperçu Excel de ce que SIRYF détient (relu via le module).
        NodeRef apercuRef = null;
        try {
            byte[] apercu = telechargerApercu(http, urlUpload, jeton, codeFichier, date);
            if (apercu != null && apercu.length > 0) {
                String nomApercu = "apercu-siryf-" + codeFichier + "-" + date + ".xlsx";
                apercuRef = ecrireFichier(fileFolderService, contentService, dossier, nomApercu,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new ByteArrayInputStream(apercu));
                LOG.info("SIRYF : aperçu déposé dans Mes fichiers de « {} » : {}/{}", transmetteur,
                        nomDossier, nomApercu);
            }
        } catch (Exception e) {
            LOG.warn("SIRYF : aperçu non récupéré ({}) — le bilan est déposé sans lien vers l'aperçu.",
                    e.toString());
        }

        // 2) Bilan HTML lisible (message + « plus de détails » vers l'aperçu).
        String html = construireBilanHtml(report, codeFichier, date, nomClasseur, apercuRef);
        ecrireFichier(fileFolderService, contentService, dossier, "bilan-" + codeFichier + "-" + date + ".html",
                "text/html", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        LOG.info("SIRYF : bilan déposé dans Mes fichiers de « {} » ({}).", transmetteur, nomDossier);
    }

    /** GET /dossier/apercu?codeFichier=..&dateArrete=.. sur le module (déduit l'URL depuis celle d'upload). */
    private byte[] telechargerApercu(HttpClient http, String urlUpload, String jeton,
                                     String codeFichier, String date) throws Exception {
        String racine = urlUpload.replaceFirst("/(dossier|sigimf)/upload.*$", "");
        String url = racine + "/dossier/apercu?codeFichier=" + enc(codeFichier) + "&dateArrete=" + enc(date);
        HttpRequest.Builder requete = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofMinutes(3)).GET();
        if (jeton != null && !jeton.isBlank()) {
            requete.header("X-Internal-Token", jeton);
        }
        HttpResponse<byte[]> reponse = http.send(requete.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (reponse.statusCode() >= 200 && reponse.statusCode() < 300) {
            return reponse.body();
        }
        throw new IllegalStateException("aperçu HTTP " + reponse.statusCode());
    }

    private String construireBilanHtml(JsonNode report, String codeFichier, String date,
                                       String nomClasseur, NodeRef apercuRef) {
        JsonNode bilan = report.path("bilan");
        int transmises = bilan.path("SUCCES").asInt(0) + bilan.path("ACCEPTE_AVEC_ERREURS").asInt(0);
        int refusees = bilan.path("ERREUR_API").asInt(0) + bilan.path("ERREUR_DONNEES").asInt(0)
                + bilan.path("ERREUR_TECHNIQUE").asInt(0);
        int differees = bilan.path("HORS_PERIODE").asInt(0);
        int deja = bilan.path("DEJA_TRANSMIS").asInt(0);

        StringBuilder motifs = new StringBuilder();
        for (JsonNode r : report.path("resultats")) {
            String outcome = r.path("outcome").asText("");
            if (MARQUEURS_ECHEC.contains(outcome)) {
                motifs.append("<li><strong>").append(echapper(r.path("feuille").asText("")))
                        .append("</strong> : ")
                        .append(echapper(motifLisible(r.path("detail").asText("")))).append("</li>\n");
            }
        }

        String shareBase = config("siriyf.share.url", "SIRIYF_SHARE_URL", "http://localhost:9090/share");
        String lien = apercuRef == null ? "" :
                "<p><a style=\"font-size:15px;font-weight:bold\" href=\"" + shareBase
                        + "/page/document-details?nodeRef=" + apercuRef.toString()
                        + "\">▶ Plus de détails : ouvrir l'aperçu SIRYF (Excel)</a></p>";

        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">"
                + "<title>Bilan de transmission SIRYF</title></head>"
                + "<body style=\"font-family:sans-serif;max-width:760px;margin:24px auto;color:#222\">"
                + "<h2>✅ Transmission SIRYF effectuée</h2>"
                + "<p>Fichier <strong>" + echapper(codeFichier) + "</strong> — arrêté au <strong>"
                + echapper(date) + "</strong> (classeur : " + echapper(nomClasseur) + ").</p>"
                + "<ul style=\"line-height:1.6\">"
                + "<li><strong>" + transmises + "</strong> feuille(s) transmise(s) à SIRYF</li>"
                + "<li><strong>" + refusees + "</strong> feuille(s) refusée(s)</li>"
                + (differees > 0 ? "<li>" + differees + " feuille(s) différée(s) (hors période)</li>" : "")
                + (deja > 0 ? "<li>" + deja + " feuille(s) déjà transmise(s)</li>" : "")
                + "</ul>"
                + (motifs.length() == 0 ? "" : "<h3>Motifs des refus</h3><ul>" + motifs + "</ul>")
                + lien
                + "<p style=\"color:#666\">Ces documents se trouvent dans « Mes fichiers » &rsaquo; "
                + "<em>Transmission " + echapper(codeFichier) + " - " + echapper(date) + "</em>.</p>"
                + "</body></html>";
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers Alfresco
    // ---------------------------------------------------------------------------------------------

    /** Retourne le sous-dossier de nom donné sous {@code parent}, en le créant s'il n'existe pas. */
    private NodeRef sousDossier(FileFolderService fileFolderService, NodeRef parent, String nom) {
        NodeRef existant = fileFolderService.searchSimple(parent, nom);
        if (existant != null) {
            return existant;
        }
        return fileFolderService.create(parent, nom, ContentModel.TYPE_FOLDER).getNodeRef();
    }

    /** Crée (ou remplace le contenu d')un fichier et y écrit le flux ; renvoie son NodeRef. */
    private NodeRef ecrireFichier(FileFolderService fileFolderService, ContentService contentService,
                                  NodeRef dossier, String nom, String mimetype, InputStream contenu) {
        NodeRef fichier = fileFolderService.searchSimple(dossier, nom);
        if (fichier == null) {
            FileInfo info = fileFolderService.create(dossier, nom, ContentModel.TYPE_CONTENT);
            fichier = info.getNodeRef();
        }
        ContentWriter writer = contentService.getWriter(fichier, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(mimetype);
        writer.setEncoding("UTF-8");
        writer.putContent(contenu);
        return fichier;
    }

    private void marquerStatut(NodeService nodeService, NodeRef doc, String statut, String nom) {
        if (TYPE_DECLARATION.equals(nodeService.getType(doc))) {
            nodeService.setProperty(doc, PROP_STATUT, statut);
            LOG.info("SIRYF : « {} » → statut « {} »", nom, statut);
        } else {
            LOG.info("SIRYF : « {} » traité (statut « {} ») — document non typé siriyf:declaration, "
                    + "propriété de statut non écrite", nom, statut);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Multipart + utilitaires
    // ---------------------------------------------------------------------------------------------

    private byte[] corpsMultipart(String frontiere, String nom, byte[] fichier,
                                  String statutDemande, String transmetteur,
                                  Integer annee, Integer mois) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String tiret = "--";
        String crlf = "\r\n";

        StringBuilder enteteFichier = new StringBuilder();
        enteteFichier.append(tiret).append(frontiere).append(crlf)
                .append("Content-Disposition: form-data; name=\"fichier\"; filename=\"")
                .append(nom.replace("\"", "_")).append("\"").append(crlf)
                .append("Content-Type: application/octet-stream").append(crlf).append(crlf);
        out.write(enteteFichier.toString().getBytes(StandardCharsets.UTF_8));
        out.write(fichier);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        champ(out, frontiere, "codeFichier", "AUTO");
        champ(out, frontiere, "feuille", "TOUS");
        champ(out, frontiere, "statut", statutDemande);
        if (transmetteur != null && !transmetteur.isBlank()) {
            champ(out, frontiere, "acteur", transmetteur);
        }
        // Période saisie sur le document (siriyf:annee/mois) : elle PRIME sur la date du classeur.
        // L'API exige les deux ensemble (sinon 400 « période incomplète ») → on n'envoie que si les deux sont là.
        if (annee != null && mois != null) {
            champ(out, frontiere, "annee", String.valueOf(annee));
            champ(out, frontiere, "mois", String.valueOf(mois));
        }

        out.write((tiret + frontiere + tiret + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void champ(ByteArrayOutputStream out, String frontiere, String nom, String valeur)
            throws Exception {
        String s = "--" + frontiere + "\r\n"
                + "Content-Disposition: form-data; name=\"" + nom + "\"\r\n\r\n"
                + valeur + "\r\n";
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] lire(ContentReader lecteur) throws Exception {
        try (InputStream in = lecteur.getContentInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static boolean contientEchec(String corpsJson) {
        for (String marqueur : MARQUEURS_ECHEC) {
            if (corpsJson.contains(marqueur)) {
                return true;
            }
        }
        return false;
    }

    private static String transmetteur(DelegateExecution execution) {
        Object v = execution.getVariable("siriyfTransmetteurUser");
        return v == null ? null : String.valueOf(v);
    }

    private static String config(String propriete, String env, String defaut) {
        String val = System.getProperty(propriete);
        if (val == null || val.isBlank()) {
            val = System.getenv(env);
        }
        return (val == null || val.isBlank()) ? defaut : val.trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Propriété d:int Alfresco → Integer (null si absente/illisible). */
    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String echapper(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Ne garde que le motif d'échec lisible : retire le préfixe technique de la requête HTTP
     * (« BCRG API 409 : », « HTTP 409 — », « 409 : »…) pour ne laisser que le message métier.
     */
    private static String motifLisible(String detail) {
        if (detail == null) {
            return "";
        }
        String s = detail.trim();
        // Préfixe optionnel « BCRG API »/« HTTP », un code à 3 chiffres, un séparateur (: - – —), espaces.
        s = s.replaceFirst("(?i)^(bcrg\\s*api|http)?\\s*\\d{3}\\s*[:\\-–—]\\s*", "");
        return s.trim();
    }

    private static String tronquer(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }
}
