package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.audit.AuditContext;
import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.pipeline.DossierIntegrationService;
import com.kimia.bcrg_integration.pipeline.DossierReport;
import com.kimia.bcrg_integration.pipeline.IntegrationResult;
import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.pipeline.JobRegistries;
import com.kimia.bcrg_integration.pipeline.PeriodeResolver;
import com.kimia.bcrg_integration.pipeline.SigimfJobRegistry;
import com.kimia.bcrg_integration.pipeline.SituJobRegistry;
import com.kimia.bcrg_integration.pipeline.TransmissionFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Ticket 8.1 : l'endpoint de déclenchement. On vérifie qu'il traduit fidèlement une demande HTTP en
 * appel de pipeline (défauts compris) et qu'une demande invalide est <strong>refusée avant</strong>
 * d'atteindre le pipeline — un dossier ne doit jamais partir à la BCRG sur une requête bancale.
 * <p>Le pipeline est remplacé par un double qui capture ses arguments : aucun classeur n'est lu,
 * aucun appel réseau n'est fait.
 */
class IntegrationControllerTest {

    private final AtomicReference<String> codeFichierRecu = new AtomicReference<>();
    private final AtomicReference<String> acteurRecu = new AtomicReference<>();
    private final AtomicReference<Collection<String>> feuillesRecues = new AtomicReference<>();
    private final AtomicReference<TransmissionImf> transmissionRecue = new AtomicReference<>();

    /** Double du pipeline : capture les arguments et rend un compte rendu de succès. */
    private final DossierIntegrationService pipeline = new DossierIntegrationService(null, null) {
        @Override
        public DossierReport run(File fichier, String codeFichier, TransmissionImf transmission,
                                 Collection<String> feuilles) {
            codeFichierRecu.set(codeFichier);
            feuillesRecues.set(feuilles);
            transmissionRecue.set(transmission);
            acteurRecu.set(AuditContext.acteurCourant().orElse(null));
            return DossierReport.of(fichier.getName(), codeFichier, transmission.getDateArrete(),
                    List.of(new IntegrationResult("M.V.PFC", Outcome.SUCCES, 3, 200, "ok")));
        }
    };

    private final BcrgSender senderFactice = (uri, body) -> ResponseEntity.ok("");

    private MockMvc mvc;
    private String cheminClasseur;
    private String cheminClasseurDate;

    @BeforeEach
    void setUp(@TempDir Path dossier) throws Exception {
        Path classeur = dossier.resolve("SIGIMF.xlsx");
        Files.writeString(classeur, "contenu factice : le pipeline est doublé");
        cheminClasseur = classeur.toAbsolutePath().toString().replace("\\", "/");
        cheminClasseurDate = classeurAvecDateDesRapports(dossier.resolve("SIGIMF-date.xlsx"));

        IntegrationController controleur = new IntegrationController(
                pipeline, new TransmissionFactory(),
                new JobRegistries(new SigimfJobRegistry(senderFactice), new SituJobRegistry(senderFactice)),
                new PeriodeResolver(new ExcelReader()));
        mvc = MockMvcBuilders.standaloneSetup(controleur)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /** Classeur minimal portant la ligne de metadonnees « Date des rapports », comme les templates. */
    private String classeurAvecDateDesRapports(Path chemin) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(chemin)) {
            Sheet feuille = wb.createSheet("M.III.HS_BILAN");
            Row entete = feuille.createRow(0);
            entete.createCell(0).setCellValue("Date des rapports");
            entete.createCell(3).setCellValue("30/06/2026");
            Row colonnes = feuille.createRow(2);
            colonnes.createCell(0).setCellValue("CODE");
            wb.write(out);
        }
        return chemin.toAbsolutePath().toString().replace("\\", "/");
    }

    @Test
    void declenche_le_pipeline_avec_les_defauts_attendus() throws Exception {
        String reponse = mvc.perform(post("/api/internal/integration/sigimf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"fichier\":\"" + cheminClasseur + "\",\"annee\":2026,\"mois\":8}")))
                .andReturn().getResponse().getContentAsString();

        assertEquals("AUTO", codeFichierRecu.get(),
                "par défaut le fichier réglementaire est déduit du classeur : "
                        + "la règle Alfresco ne le connaît pas");
        assertEquals(List.of("TOUS"), feuillesRecues.get(), "toutes les feuilles par défaut");
        assertEquals(TransmissionImf.StatutEnum.CREATION, transmissionRecue.get().getStatut());
        // règle métier : dateArrete = dernier jour du mois
        assertEquals(31, transmissionRecue.get().getDateArrete().getDayOfMonth());
        assertTrue(reponse.contains("SUCCES"), reponse);
    }

    @Test
    void transmet_la_feuille_et_le_statut_demandes() throws Exception {
        mvc.perform(post("/api/internal/integration/sigimf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"fichier\":\"" + cheminClasseur + "\",\"annee\":2026,\"mois\":2,"
                        + "\"feuille\":\"M.V.PFC\",\"statut\":\"MODIFICATION\"}")));

        assertEquals(List.of("M.V.PFC"), feuillesRecues.get());
        assertEquals(TransmissionImf.StatutEnum.MODIFICATION, transmissionRecue.get().getStatut());
        assertEquals(28, transmissionRecue.get().getDateArrete().getDayOfMonth(), "février 2026");
    }

    /**
     * Sans période dans la demande, la date d'arrêté vient du classeur : c'est le cas nominal du
     * workflow Alfresco, où l'approbation du validateur déclenche l'envoi sans aucune saisie.
     */
    @Test
    void deduit_la_periode_du_classeur_quand_la_demande_n_en_donne_pas() throws Exception {
        mvc.perform(post("/api/internal/integration/dossier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"fichier\":\"" + cheminClasseurDate + "\"}")));

        assertEquals("2026-06-30", transmissionRecue.get().getDateArrete().toLocalDate().toString());
    }

    /** L'acteur qui a déclenché l'envoi (le transmetteur du workflow SIRYF) doit se retrouver dans la piste d'audit (§9). */
    @Test
    void transmet_l_acteur_comme_acteur_d_audit() throws Exception {
        mvc.perform(post("/api/internal/integration/dossier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"fichier\":\"" + cheminClasseur + "\",\"annee\":2026,\"mois\":8,"
                        + "\"acteur\":\"joyce.transmetteur\"}")));

        assertEquals("joyce.transmetteur", acteurRecu.get());
    }

    @Test
    void refuse_une_periode_incomplete() throws Exception {
        int statut = mvc.perform(post("/api/internal/integration/sigimf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"fichier\":\"" + cheminClasseur + "\",\"annee\":2026}")))
                .andReturn().getResponse().getStatus();

        assertEquals(400, statut);
        assertNull(codeFichierRecu.get(), "le pipeline ne doit pas être appelé");
    }

    @Test
    void refuse_une_demande_sans_periode_sur_un_classeur_muet() throws Exception {
        var reponse = mvc.perform(post("/api/internal/integration/sigimf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"fichier\":\"" + cheminClasseur + "\"}")))
                .andReturn().getResponse();

        // plutôt que de deviner : une mauvaise dateArrete consomme un couple (feuille, période)
        assertEquals(400, reponse.getStatus());
        assertTrue(reponse.getContentAsString().contains("Période"), reponse.getContentAsString());
        assertNull(codeFichierRecu.get(), "le pipeline ne doit pas être appelé");
    }

    @Test
    void refuse_un_classeur_introuvable() throws Exception {
        var reponse = mvc.perform(post("/api/internal/integration/sigimf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"fichier\":\"C:/nexiste/pas.xlsx\",\"annee\":2026,\"mois\":8}")))
                .andReturn().getResponse();

        assertEquals(400, reponse.getStatus());
        assertTrue(reponse.getContentAsString().contains("introuvable"), reponse.getContentAsString());
        assertNull(codeFichierRecu.get(), "le pipeline ne doit pas être appelé");
    }

    @Test
    void refuse_un_statut_de_transmission_inconnu() throws Exception {
        int statut = mvc.perform(post("/api/internal/integration/sigimf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"fichier\":\"" + cheminClasseur + "\",\"annee\":2026,\"mois\":8,"
                                + "\"statut\":\"BROUILLON\"}")))
                .andReturn().getResponse().getStatus();

        assertEquals(400, statut);
    }

    @Test
    void expose_les_feuilles_couvertes() throws Exception {
        String reponse = mvc.perform(get("/api/internal/integration/feuilles"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(reponse.contains("M.I.BILAN"), reponse);
        assertTrue(reponse.contains("M.XV.RECAP"), reponse);
    }
}
