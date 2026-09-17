package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationBilanActifString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationBilanPassifImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationHorsBilanImfString;
import com.kimia.bcrg_integration.client.dto.SituationBilanActif;
import com.kimia.bcrg_integration.excel.ExcelReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide le câblage du fichier <strong>SITU</strong> — deuxième fichier réglementaire — <em>sur le
 * template réel</em> (`samples/SITU.xlsx`), sans réseau : le {@link BcrgSender} est une lambda qui
 * capture l'appel.
 * <p>Lire le vrai fichier est ici essentiel : SITU_01 et SITU_02 se poursuivent dans un second bloc
 * et le template mêle aux données des lignes de titre, une ligne de numérotation de colonnes et des
 * renvois de compte. Des données synthétiques ne prouveraient rien de tout cela.
 */
class SituJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());
    private final BcrgSender accepte = (uri, body) -> ResponseEntity.ok("Traitement effectue avec succes");

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SITU.xlsx");
        assertNotNull(in, "Échantillon samples/SITU.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void connait_les_trois_feuilles_situ() {
        SituJobRegistry registry = new SituJobRegistry(accepte);

        assertEquals("SITU", registry.codeFichier());
        assertEquals(List.of("SITU_01", "SITU_02", "SITU_03"), List.copyOf(registry.sheets()));
        assertNull(registry.forSheet("Liste-Situ-Annexes"), "feuille-liste : pas un tableau à transmettre");
    }

    @Test
    void situ01_part_en_un_seul_envoi_avec_ses_deux_blocs() throws Exception {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<Object> body = new AtomicReference<>();
        SituJobRegistry registry = new SituJobRegistry((u, b) -> {
            uri.set(u);
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet("SITU_01"),
                    transmissions.creationForMonth(2026, 6));

            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            // l'en-tête répété du 2e bloc porte lui aussi un « N° Compte » : c'est le lecteur qui l'écarte
            assertEquals(40, r.itemCount(), "40 lignes de situation sur les deux blocs");
            assertEquals("/api/imf/situ/situ01", uri.get());

            DataModelImfSituationBilanActifString payload =
                    assertInstanceOf(DataModelImfSituationBilanActifString.class, body.get());
            assertNull(payload.getItems2(), "items2 doit partir à null, jamais en liste vide");
            List<SituationBilanActif> items = payload.getItems();
            assertEquals("1", items.get(0).getNumCompte(), "1re ligne du 1er bloc");
            assertEquals("44", items.get(items.size() - 1).getNumCompte(), "dernière ligne du 2e bloc");
            assertTrue(items.stream().anyMatch(i -> "30".equals(i.getNumCompte())),
                    "les lignes du 2e bloc (« ACTIF (2) ») sont bien dans le même envoi");
        }
    }

    @Test
    void les_renvois_de_compte_ne_sont_pas_lus_comme_des_montants() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        SituJobRegistry registry = new SituJobRegistry((u, b) -> {
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            // sans la règle, « N°C 390 » dans la colonne des provisions ferait échouer toute la feuille
            IntegrationResult r = service.run(wb, registry.forSheet("SITU_01"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());

            DataModelImfSituationBilanActifString payload =
                    (DataModelImfSituationBilanActifString) body.get();
            SituationBilanActif ligne = payload.getItems().stream()
                    .filter(i -> "30".equals(i.getNumCompte()))
                    .findFirst().orElseThrow();
            assertNull(ligne.getProvisionAmortissement(), "« N°C 390 » est une consigne, pas un montant");
        }
    }

    @Test
    void situ02_et_situ03_partent_sur_leurs_propres_endpoints() throws Exception {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<Object> body = new AtomicReference<>();
        SituJobRegistry registry = new SituJobRegistry((u, b) -> {
            uri.set(u);
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult passif = service.run(wb, registry.forSheet("SITU_02"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, passif.outcome(), passif.detail());
            assertEquals(36, passif.itemCount(), "SITU_02 : deux blocs également");
            assertEquals("/api/imf/situ/situ02", uri.get());
            assertInstanceOf(DataModelImfSituationBilanPassifImfString.class, body.get());

            IntegrationResult horsBilan = service.run(wb, registry.forSheet("SITU_03"),
                    transmissions.creationForMonth(2026, 6));
            assertEquals(IntegrationResult.Outcome.SUCCES, horsBilan.outcome(), horsBilan.detail());
            assertEquals(16, horsBilan.itemCount(), "SITU_03 : un seul bloc");
            assertEquals("/api/imf/situ/situ03", uri.get());
            assertInstanceOf(DataModelImfSituationHorsBilanImfString.class, body.get());
        }
    }
}
