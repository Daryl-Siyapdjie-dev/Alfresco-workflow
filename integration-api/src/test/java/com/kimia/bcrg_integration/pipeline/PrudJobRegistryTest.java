package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfCalculFondsPropresNetsString;
import com.kimia.bcrg_integration.client.dto.DataModelImfConformiteNormesPrudentiellesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfImfCollectantDepotString;
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
 * Câblage du fichier <strong>PRUD</strong> — 12 feuilles, 12 endpoints — vérifié <em>sur le template
 * réel</em> ({@code samples/PRUD.xlsx}), sans réseau : le {@link BcrgSender} capture les appels.
 * <p>Ce test est le filet du registre : une erreur de câblage enverrait une feuille sur l'endpoint
 * d'une autre, ce que ni le compilateur ni les tests de mappers ne verraient.
 */
class PrudJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());
    private final BcrgSender accepte = (uri, body) -> ResponseEntity.ok("Traitement effectue avec succes");

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/PRUD.xlsx");
        assertNotNull(in, "Échantillon samples/PRUD.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void connait_les_douze_feuilles_du_fichier() {
        PrudJobRegistry registry = new PrudJobRegistry(accepte);

        assertEquals("PRUD", registry.codeFichier());
        assertEquals(List.of("ECNP", "FPNC", "FPN", "APR", "PRUD_01", "PRUD_02", "PRUD_03",
                        "PRUD_04", "PRUD_05", "PRUD_06", "IMAO_ICD", "IMAO_INCD"),
                List.copyOf(registry.sheets()));
        assertNull(registry.forSheet("PRUD_07"), "feuille inexistante");
    }

    /**
     * Le vrai enjeu du registre : chaque feuille sur <em>son</em> endpoint. On les traite toutes
     * d'affilée sur le template réel, ce qui vérifie du même coup que les 12 mappers passent la
     * lecture sans lever d'erreur de données.
     */
    @Test
    void chaque_feuille_part_sur_son_endpoint_avec_le_bon_nombre_d_items() throws Exception {
        Map<String, String> endpoints = new LinkedHashMap<>();
        List<String> refus = new ArrayList<>();
        Map<String, Integer> items = new LinkedHashMap<>();

        AtomicReference<String> derniereUri = new AtomicReference<>();
        PrudJobRegistry registry = new PrudJobRegistry((uri, body) -> {
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
        assertEquals(Map.of(
                "ECNP", "/api/imf/prud/ecnp",
                "FPNC", "/api/imf/prud/fpnc",
                "FPN", "/api/imf/prud/fpn",
                "APR", "/api/imf/prud/apr",
                "PRUD_01", "/api/imf/prud/prud_01",
                "PRUD_02", "/api/imf/prud/prud_02",
                "PRUD_03", "/api/imf/prud/prud_03",
                "PRUD_04", "/api/imf/prud/prud_04",
                "PRUD_05", "/api/imf/prud/prud_05",
                "PRUD_06", "/api/imf/prud/prud_06"), filtrer(endpoints, "IMAO_ICD", "IMAO_INCD"));
        assertEquals("/api/imf/prud/imao_icd", endpoints.get("IMAO_ICD"));
        assertEquals("/api/imf/prud/imao_incd", endpoints.get("IMAO_INCD"));

        assertEquals(13, items.get("ECNP"));
        assertEquals(9, items.get("FPNC"));
        assertEquals(33, items.get("FPN"));
        assertEquals(16, items.get("APR"));
        assertEquals(9, items.get("PRUD_01"));
        assertEquals(11, items.get("PRUD_02"));
        assertEquals(23, items.get("PRUD_03"));
        assertEquals(15, items.get("PRUD_04"));
        assertEquals(5, items.get("PRUD_05"));
        assertEquals(19, items.get("PRUD_06"));
        assertEquals(46, items.get("IMAO_ICD"));
        assertEquals(43, items.get("IMAO_INCD"));
    }

    /** La règle « items2 à null » vaut pour tous les fichiers : un tableau vide vide le corps aux yeux de SIRYF. */
    @Test
    void aucun_payload_ne_porte_de_liste_items2_vide() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        PrudJobRegistry registry = new PrudJobRegistry((uri, corps) -> {
            body.set(corps);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            service.run(wb, registry.forSheet("ECNP"), transmissions.creationForMonth(2026, 6));
            var ecnp = assertInstanceOf(DataModelImfConformiteNormesPrudentiellesString.class, body.get());
            assertNull(ecnp.getItems2());
            assertNotNull(ecnp.getTransmission().getDateArrete());

            service.run(wb, registry.forSheet("FPN"), transmissions.creationForMonth(2026, 6));
            assertNull(assertInstanceOf(DataModelImfCalculFondsPropresNetsString.class, body.get()).getItems2());

            service.run(wb, registry.forSheet("IMAO_ICD"), transmissions.creationForMonth(2026, 6));
            assertNull(assertInstanceOf(DataModelImfImfCollectantDepotString.class, body.get()).getItems2());
        }
    }

    private static Map<String, String> filtrer(Map<String, String> source, String... exclus) {
        Map<String, String> copie = new LinkedHashMap<>(source);
        for (String cle : exclus) copie.remove(cle);
        return copie;
    }
}
