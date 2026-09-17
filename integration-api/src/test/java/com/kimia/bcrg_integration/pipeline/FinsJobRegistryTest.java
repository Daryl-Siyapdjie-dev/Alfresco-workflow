package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.CompteResultatCharges;
import com.kimia.bcrg_integration.client.dto.DataModelImfBalanceAgeePortefeuilleString;
import com.kimia.bcrg_integration.client.dto.DataModelImfChargePersonnelImfNombreEmployeImf;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompteResultatProduitCompteResultatCharges;
import com.kimia.bcrg_integration.client.dto.DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance;
import com.kimia.bcrg_integration.client.dto.DataModelImfBilanHorsBilanImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfEtablissementCreditImfVentilationTypeDetenteurs;
import com.kimia.bcrg_integration.client.dto.DataModelImfImmobiisationAmmortissementImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfRessourceCollecteImfString;
import com.kimia.bcrg_integration.client.dto.RessourceCollecteImf;
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
 * Le fichier <strong>FINS</strong>, sur son template réel ({@code samples/FINS.xlsx}) : les 12 feuilles
 * qui ont un endpoint IMF, dont les quatre qui empilent <strong>deux tableaux</strong>.
 * <p>Deux choses s'y jouent, et aucune ne se manifeste par une erreur si elle est fausse :
 * le <strong>nombre de lignes de chapeaux</strong> au-dessus des intitulés (trois sur FINS_01, quatre
 * sur FINS_02, une seule sur FINS_17) — se tromper vide le mapping ou nomme une colonne « 1 » ; et la
 * <strong>frontière entre les deux tableaux</strong> d'une même feuille — se tromper ferait partir
 * les charges parmi les produits.
 */
class FinsJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());
    private final BcrgSender accepte = (uri, body) -> ResponseEntity.ok("Traitement effectue avec succes");

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/FINS.xlsx");
        assertNotNull(in, "Échantillon samples/FINS.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void declare_les_douze_feuilles_qui_ont_un_endpoint_imf() {
        FinsJobRegistry registry = new FinsJobRegistry(accepte);

        assertEquals("FINS", registry.codeFichier());
        assertEquals(List.of("FINS_01", "FINS_02", "FINS_03", "FINS_04",
                        "FINS_07_GNF", "FINS_08_GNF", "FINS_15", "FINS_17",
                        "FINS_09", "FINS_12", "FINS_14", "FINS_16"),
                List.copyOf(registry.sheets()));
        // FINS_05/06/10/11/13 n'ont pas d'endpoint IMF : question ouverte côté BCRG
        assertNull(registry.forSheet("FINS_05"));
        assertNull(registry.forSheet("FINS_13"));
    }

    /**
     * Le cœur du sujet : {@code FINS_09} liste ses produits, puis ses charges sous un en-tête
     * « CODE ». Les deux partent dans le même envoi, mais dans deux listes distinctes — et
     * <strong>aucune ligne de charges ne doit se retrouver parmi les produits</strong>.
     */
    @Test
    void fins09_separe_les_produits_des_charges_dans_deux_listes() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("FINS_09"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            var payload = assertInstanceOf(
                    DataModelImfCompteResultatProduitCompteResultatCharges.class, body.get());
            assertEquals(36, payload.getItems().size(), "les produits, et eux seuls");
            assertEquals(37, payload.getItems2().size(), "les charges, dans items2");
            assertEquals(73, r.itemCount(), "le compte de la feuille additionne les deux tableaux");

            assertTrue(payload.getItems().get(0).getLibelle().contains("PRODUITS"),
                    payload.getItems().get(0).getLibelle());
            assertTrue(payload.getItems().stream().noneMatch(i -> i.getCode().startsWith("RC6_")),
                    "aucun code de charge (RC6_…) ne doit figurer parmi les produits");
            assertTrue(payload.getItems().stream().noneMatch(i -> "FINS_09".equals(i.getCode())),
                    "le nom de la feuille, écrit dans le bandeau, n'est pas un code de ligne "
                            + "— SIRYF répondait « Code FINS_09 est introuvable »");
            CompteResultatCharges premiereCharge = payload.getItems2().get(0);
            assertEquals("RC6_0010", premiereCharge.getCode());
            assertTrue(premiereCharge.getLibelle().contains("CHARGES"), premiereCharge.getLibelle());
        }
    }

    /**
     * Les trois autres feuilles à deux tableaux partagent le même marqueur pour les deux : c'est
     * l'<em>occurrence</em> qui les distingue, et {@code stoppingAt} qui borne le premier.
     */
    @Test
    void les_feuilles_au_marqueur_repete_visent_la_bonne_occurrence() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            service.run(wb, registry.forSheet("FINS_12"), transmissions.creationForMonth(2026, 6));
            var souffrance = assertInstanceOf(
                    DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance.class, body.get());
            assertEquals(7, souffrance.getItems().size(), "encours bruts");
            assertEquals(6, souffrance.getItems2().size(), "provisions requises par la BCRG");

            service.run(wb, registry.forSheet("FINS_14"), transmissions.creationForMonth(2026, 6));
            var personnel = assertInstanceOf(
                    DataModelImfChargePersonnelImfNombreEmployeImf.class, body.get());
            assertEquals(6, personnel.getItems().size(), "charges de personnel");
            assertEquals(8, personnel.getItems2().size(), "nombre d'employés");
            // le second tableau ventile par lieu et par sexe : trois couples FEMME/HOMME homonymes
            assertTrue(personnel.getItems2().stream().anyMatch(e -> e.getLibelle() != null
                    && e.getLibelle().contains("Cadres supérieurs")));
        }
    }

    @Test
    void chaque_feuille_part_sur_son_endpoint_avec_le_bon_nombre_d_items() throws Exception {
        Map<String, String> endpoints = new LinkedHashMap<>();
        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> refus = new ArrayList<>();

        AtomicReference<String> derniereUri = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, body) -> {
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
        assertEquals(Map.ofEntries(
                        Map.entry("FINS_01", "/api/imf/fins/fins01"),
                        Map.entry("FINS_02", "/api/imf/fins/fins02"),
                        Map.entry("FINS_03", "/api/imf/fins/fins03"),
                        Map.entry("FINS_04", "/api/imf/fins/fins04"),
                        Map.entry("FINS_07_GNF", "/api/imf/fins/fins07GNF"),
                        Map.entry("FINS_08_GNF", "/api/imf/fins/fins08GNF"),
                        Map.entry("FINS_09", "/api/imf/fins/fins09"),
                        Map.entry("FINS_12", "/api/imf/fins/fins12"),
                        Map.entry("FINS_14", "/api/imf/fins/fins14"),
                        Map.entry("FINS_15", "/api/imf/fins/fins15"),
                        Map.entry("FINS_16", "/api/imf/fins/fins16"),
                        Map.entry("FINS_17", "/api/imf/fins/fins17")),
                endpoints);
        // pour les feuilles à deux tableaux, le compte additionne les deux listes
        assertEquals(Map.ofEntries(
                        Map.entry("FINS_01", 7), Map.entry("FINS_02", 11), Map.entry("FINS_03", 12),
                        Map.entry("FINS_04", 12), Map.entry("FINS_07_GNF", 74),
                        Map.entry("FINS_08_GNF", 22), Map.entry("FINS_09", 73),
                        Map.entry("FINS_12", 13), Map.entry("FINS_14", 14), Map.entry("FINS_15", 25),
                        Map.entry("FINS_16", 19), Map.entry("FINS_17", 8)),
                items);
    }

    /**
     * FINS_01 est la feuille qui justifie un en-tête sur trois lignes : « RESIDENTS » chapeaute
     * « Ménages », qui chapeaute « Particuliers ». Si la forme était fausse, les items partiraient
     * avec des montants vides — un échec silencieux.
     */
    @Test
    void fins01_resout_les_colonnes_du_troisieme_niveau_de_chapeau() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("FINS_01"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            var payload = assertInstanceOf(DataModelImfRessourceCollecteImfString.class, body.get());
            assertNull(payload.getItems2(), "items2 doit partir à null, jamais en liste vide");
            List<RessourceCollecteImf> lignes = payload.getItems();
            assertEquals("RL2_0110", lignes.get(0).getCode());
            assertEquals("241", lignes.get(0).getNumCpte());
            assertTrue(lignes.get(0).getLibelle().contains("Dépôts à vue"), lignes.get(0).getLibelle());
            // écart assumé : le template n'a qu'une colonne « Etat », le DTO en attend trois
            assertTrue(lignes.stream().allMatch(i -> i.getAdminCentrale() == null),
                    "aucune colonne « administration centrale » dans la source");
        }
    }

    /** FINS_03 : deux couples GNF/% homonymes, plus une colonne de pourcentage lue par position. */
    @Test
    void fins03_distingue_les_colonnes_homonymes_bilan_et_hors_bilan() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("FINS_03"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            var payload = assertInstanceOf(DataModelImfBilanHorsBilanImfString.class, body.get());
            assertEquals(12, payload.getItems().size());
            assertEquals("RA2_1090", payload.getItems().get(0).getCode());
            assertTrue(payload.getItems().get(0).getLibelle().contains("Agriculture"));
        }
    }

    /** Les deux feuilles de titres partagent un mapper : seul l'intitulé des libellés change. */
    @Test
    void les_deux_feuilles_de_titres_partagent_leur_mapper() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            service.run(wb, registry.forSheet("FINS_07_GNF"), transmissions.creationForMonth(2026, 6));
            var detenus = assertInstanceOf(
                    DataModelImfEtablissementCreditImfVentilationTypeDetenteurs.class, body.get());
            assertTrue(detenus.getItems().get(0).getLibelle().contains("TITRES"),
                    detenus.getItems().get(0).getLibelle());

            service.run(wb, registry.forSheet("FINS_08_GNF"), transmissions.creationForMonth(2026, 6));
            var emis = assertInstanceOf(
                    DataModelImfEtablissementCreditImfVentilationTypeDetenteurs.class, body.get());
            assertEquals("RL3_0050", emis.getItems().get(0).getCode());
            assertNull(emis.getItems2());
        }
    }

    @Test
    void fins15_separe_ses_deux_colonnes_total_homonymes() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("FINS_15"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(25, assertInstanceOf(
                    DataModelImfImmobiisationAmmortissementImfString.class, body.get()).getItems().size());
        }
    }

    @Test
    void fins17_lit_la_balance_agee_avec_son_en_tete_a_une_ligne() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        FinsJobRegistry registry = new FinsJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("FINS_17"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(8, assertInstanceOf(
                    DataModelImfBalanceAgeePortefeuilleString.class, body.get()).getItems().size());
        }
    }
}
