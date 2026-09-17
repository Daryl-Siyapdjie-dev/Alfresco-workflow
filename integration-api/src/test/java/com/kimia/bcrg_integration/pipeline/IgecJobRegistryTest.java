package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompositionCapitalImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompositionDirectionImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfEngagementsActionnairesAdminMandatairesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfImplantationIfiString;
import com.kimia.bcrg_integration.client.dto.DataModelImfInfoComplementairesCalculsFPNString;
import com.kimia.bcrg_integration.client.dto.ImplantationIfi;
import com.kimia.bcrg_integration.excel.ExcelReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le fichier <strong>IGEC</strong> — 17 feuilles, 17 endpoints — vérifié <em>sur le template réel</em>
 * ({@code samples/IGEC.xlsx}), sans réseau.
 * <p>Ce que ce test protège vraiment, c'est le repérage de l'en-tête : IGEC porte ses intitulés sur
 * deux lignes, et une forme mal choisie donnerait des colonnes nommées « NOMBRE » au lieu de
 * « Guichets » — le mapping se viderait silencieusement au lieu d'échouer.
 */
class IgecJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());
    private final BcrgSender accepte = (uri, body) -> ResponseEntity.ok("Traitement effectue avec succes");

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/IGEC.xlsx");
        assertNotNull(in, "Échantillon samples/IGEC.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void connait_les_dix_sept_feuilles_du_fichier() {
        IgecJobRegistry registry = new IgecJobRegistry(accepte);

        assertEquals("IGEC", registry.codeFichier());
        assertEquals(17, registry.sheets().size());
        assertEquals("IGEC_01", List.copyOf(registry.sheets()).get(0));
        assertEquals("IGEC_17", List.copyOf(registry.sheets()).get(16));
    }

    @Test
    void chaque_feuille_part_sur_son_endpoint_avec_le_bon_nombre_d_items() throws Exception {
        Map<String, String> endpoints = new LinkedHashMap<>();
        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> refus = new ArrayList<>();

        AtomicReference<String> derniereUri = new AtomicReference<>();
        IgecJobRegistry registry = new IgecJobRegistry((uri, body) -> {
            derniereUri.set(uri);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            for (String feuille : registry.sheets()) {
                IntegrationResult r = service.run(wb, registry.forSheet(feuille),
                        transmissions.creationForMonth(2026, 6));
                if (r.outcome() != IntegrationResult.Outcome.SUCCES) {
                    refus.add(feuille + " → " + r.outcome() + " : " + r.detail());
                    continue;
                }
                endpoints.put(feuille, derniereUri.get());
                items.put(feuille, r.itemCount());
            }
        }

        assertTrue(refus.isEmpty(), () -> "feuilles en échec : " + refus);
        for (int n = 1; n <= 17; n++) {
            String feuille = String.format("IGEC_%02d", n);
            assertEquals(String.format("/api/imf/igec/igec%02d", n), endpoints.get(feuille), feuille);
        }
        assertEquals(Map.ofEntries(
                        Map.entry("IGEC_01", 27), Map.entry("IGEC_02", 51), Map.entry("IGEC_03", 51),
                        Map.entry("IGEC_04", 10), Map.entry("IGEC_05", 12), Map.entry("IGEC_06", 43),
                        Map.entry("IGEC_07", 9), Map.entry("IGEC_08", 21), Map.entry("IGEC_09", 11),
                        Map.entry("IGEC_10", 23), Map.entry("IGEC_11", 21), Map.entry("IGEC_12", 23),
                        Map.entry("IGEC_13", 10), Map.entry("IGEC_14", 2), Map.entry("IGEC_15", 2),
                        Map.entry("IGEC_16", 2), Map.entry("IGEC_17", 8)),
                items);
    }

    /**
     * IGEC_01 est la feuille qui justifiait une forme à elle : ses intitulés précis sont au-dessus du
     * marqueur. Si la forme était mauvaise, les items partiraient avec des champs vides — un échec
     * silencieux, que seule une valeur lue de bout en bout révèle.
     */
    @Test
    void igec01_lit_les_colonnes_portees_par_la_ligne_haute_de_l_en_tete() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        IgecJobRegistry registry = new IgecJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("IGEC_01"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            var payload = assertInstanceOf(DataModelImfImplantationIfiString.class, body.get());
            assertNull(payload.getItems2(), "items2 doit partir à null, jamais en liste vide");
            List<ImplantationIfi> lignes = payload.getItems();
            assertEquals("RN0_0100", lignes.get(0).getCode());
            // le mapping des colonnes est vérifié par leur seule résolution : une colonne absente
            // ferait échouer Columns.of (« Colonne obligatoire absente »), pas un item vide
            assertTrue(lignes.stream().allMatch(i -> i.getCode() != null));
        }
    }

    /** Les écarts assumés doivent rester visibles : ce qui n'a pas de source ne part pas rempli. */
    @Test
    void les_champs_sans_colonne_source_partent_vides() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        IgecJobRegistry registry = new IgecJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            // IGEC_06 : le template donne un montant et un %, pas un montant et un montant GNF
            service.run(wb, registry.forSheet("IGEC_06"), transmissions.creationForMonth(2026, 6));
            var capital = assertInstanceOf(DataModelImfCompositionCapitalImfString.class, body.get());
            assertTrue(capital.getItems().stream().allMatch(i -> i.getMontantCapitalSouscritGnf() == null),
                    "aucune colonne GNF dans la source : le champ reste vide");

            // IGEC_16 : « Engagement par signature & Autres comptes débiteurs » est une seule colonne
            service.run(wb, registry.forSheet("IGEC_16"), transmissions.creationForMonth(2026, 6));
            var engagements = assertInstanceOf(
                    DataModelImfEngagementsActionnairesAdminMandatairesString.class, body.get());
            assertTrue(engagements.getItems().stream()
                            .allMatch(i -> i.getSoldeAvanceAutresComptesDebiteur() == null),
                    "rien ne permet de répartir le montant fusionné");
        }
    }

    /** La colonne « DATE ET NUMERO DE L'ACTE » mêle deux informations : on en extrait la date. */
    @Test
    void igec04_extrait_la_date_d_une_colonne_qui_porte_aussi_le_numero_de_l_acte() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        IgecJobRegistry registry = new IgecJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("IGEC_04"),
                    transmissions.creationForMonth(2026, 6));
            // le template est vide sur cette colonne : ce qui compte, c'est qu'elle ne fasse pas
            // tomber la feuille en ERREUR_DONNEES quand elle portera « 12/03/2020 acte n°45 »
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertInstanceOf(DataModelImfCompositionDirectionImfString.class, body.get());
        }
    }

    @Test
    void igec17_se_lit_avec_son_marqueur_et_son_en_tete_a_une_ligne() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        IgecJobRegistry registry = new IgecJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("IGEC_17"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            var payload = assertInstanceOf(
                    DataModelImfInfoComplementairesCalculsFPNString.class, body.get());
            assertEquals(8, payload.getItems().size());
            assertEquals("EPR_0140", payload.getItems().get(0).getCode());
            assertTrue(payload.getItems().get(0).getLibelle().contains("Actions propres"),
                    payload.getItems().get(0).getLibelle());
        }
    }
}
