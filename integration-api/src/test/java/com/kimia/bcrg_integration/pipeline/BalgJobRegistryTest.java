package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.BalanceImf;
import com.kimia.bcrg_integration.client.dto.DataModelImfBalanceImfString;
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
 * Valide le câblage du fichier <strong>BALG</strong> (balance générale) sur le template réel
 * {@code samples/BALG.xlsx} — le dernier fichier du périmètre, et le seul dont l'onglet porte le
 * millésime de l'exercice.
 * <p>Trois propriétés méritent une épreuve : les six colonnes de montants homonymes doivent tomber
 * sur les bons champs (une inversion enverrait les mouvements à la place des soldes, sans qu'aucune
 * erreur ne se voie), les lignes de totaux du bas de tableau ne doivent pas partir, et l'onglet doit
 * rester reconnu quand son millésime change.
 * <p>Hors-ligne : le {@link BcrgSender} est une lambda qui capture l'appel.
 */
class BalgJobRegistryTest {

    private final TransmissionFactory transmissions = new TransmissionFactory();
    private final IntegrationService service = new IntegrationService(new ExcelReader());

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/BALG.xlsx");
        assertNotNull(in, "Échantillon samples/BALG.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void reconnait_l_onglet_de_balance_quel_que_soit_son_millesime() {
        BalgJobRegistry registry = new BalgJobRegistry((u, b) -> ResponseEntity.ok("ok"));

        assertEquals("BALG", registry.codeFichier());
        assertEquals(List.of("BALANCE H.IMF2026"), List.copyOf(registry.sheets()));
        assertNotNull(registry.forSheet("BALANCE H.IMF2026"));
        assertNotNull(registry.forSheet("BALANCE H.IMF2027"),
                "l'exercice suivant ne doit pas faire disparaître la balance");
        assertEquals("BALANCE H.IMF2027", registry.forSheet("BALANCE H.IMF2027").sheetName(),
                "le job porte le nom réel de l'onglet : c'est sous ce nom qu'il sera relu");
        assertNull(registry.forSheet("M.I.BILAN"), "une feuille d'un autre fichier");
    }

    @Test
    void la_balance_part_en_un_envoi_sur_l_endpoint_des_imf() throws Exception {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<Object> body = new AtomicReference<>();
        BalgJobRegistry registry = new BalgJobRegistry((u, b) -> {
            uri.set(u);
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            IntegrationResult r = service.run(wb, registry.forSheet(BalgJobRegistry.FEUILLE),
                    transmissions.creationForMonth(2026, 6));

            assertEquals(IntegrationResult.Outcome.SUCCES, r.outcome(), r.detail());
            assertEquals(315, r.itemCount(), "les 315 comptes du plan comptable, sans les totaux");
            assertEquals("/api/balanceimf", uri.get(), "et non /api/balance, qui est celui des banques");

            DataModelImfBalanceImfString payload =
                    assertInstanceOf(DataModelImfBalanceImfString.class, body.get());
            assertNull(payload.getItems2(), "items2 doit partir à null, jamais en liste vide");

            List<BalanceImf> items = payload.getItems();
            assertEquals("1", items.get(0).getChapitre(), "1re ligne : le compte de classe 1");
            assertEquals("OPERATIONS AVEC LE SECTEUR FINANCIER", items.get(0).getIntituleChapitre());
            assertEquals("GNF", items.get(0).getCodeDevise());
            assertEquals("99", items.get(items.size() - 1).getChapitre(), "dernière ligne du tableau");
        }
    }

    @Test
    void les_lignes_de_totaux_ne_sont_pas_transmises() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        BalgJobRegistry registry = new BalgJobRegistry((u, b) -> {
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            service.run(wb, registry.forSheet(BalgJobRegistry.FEUILLE),
                    transmissions.creationForMonth(2026, 6));

            // SIRYF recalcule ses agrégats : transmettre « TOTAUX COMPTES DE BILAN » compterait double
            List<BalanceImf> items = ((DataModelImfBalanceImfString) body.get()).getItems();
            assertTrue(items.stream().allMatch(i -> i.getChapitre().chars().allMatch(Character::isDigit)),
                    "seules des lignes portant un numéro de compte doivent partir");
            assertTrue(items.stream().noneMatch(i -> i.getIntituleChapitre() == null
                            && i.getChapitre().isEmpty()),
                    "aucune ligne vide du bloc de consignes en bas de feuille");
        }
    }

    /**
     * Les trois paires DEBIT/CREDIT du template s'appellent toutes « DEBIT » et « CREDIT ». Si le
     * mapper les confondait, les mouvements de la période partiraient à la place des soldes — une
     * balance fausse, mais acceptée sans broncher. On le vérifie sur une ligne remplie à la main,
     * avec neuf valeurs distinctes et traçables.
     */
    @Test
    void chaque_paire_debit_credit_tombe_sur_son_propre_champ() throws Exception {
        AtomicReference<Object> body = new AtomicReference<>();
        BalgJobRegistry registry = new BalgJobRegistry((u, b) -> {
            body.set(b);
            return ResponseEntity.ok("ok");
        });

        try (Workbook wb = ouvrirTemplate()) {
            // compte 1011 « Monnaie électronique », ligne 11 du classeur (index POI 10)
            org.apache.poi.ss.usermodel.Row ligne =
                    wb.getSheet(BalgJobRegistry.FEUILLE).getRow(10);
            double[] montants = {11, 12, 21, 22, 31, 32};      // initial D/C, mouvement D/C, final D/C
            for (int i = 0; i < montants.length; i++) {
                ligne.createCell(3 + i).setCellValue(montants[i]);
            }

            service.run(wb, registry.forSheet(BalgJobRegistry.FEUILLE),
                    transmissions.creationForMonth(2026, 6));

            BalanceImf compte = ((DataModelImfBalanceImfString) body.get()).getItems().stream()
                    .filter(i -> "1011".equals(i.getChapitre())).findFirst().orElseThrow();
            assertEquals(11d, compte.getInitialDebit());
            assertEquals(12d, compte.getInitialCredit());
            assertEquals(21d, compte.getMouvementDebit());
            assertEquals(22d, compte.getMouvementCredit());
            assertEquals(31d, compte.getSoldeFinalDebit());
            assertEquals(32d, compte.getSoldeFinalCredit());
        }
    }
}
