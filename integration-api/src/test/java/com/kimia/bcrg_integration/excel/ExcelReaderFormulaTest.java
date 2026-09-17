package com.kimia.bcrg_integration.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valide le recalcul des <strong>formules périmées</strong> sur le vrai fichier
 * {@code source-SIGIMF.xlsx} — un classeur rempli par openpyxl qui conserve les
 * formules du template sans les recalculer (cache = 0 ou None).
 *
 * <p>Le cas critique : la colonne « Montant net » du bilan, qui est toujours
 * {@code =D-E} (Montant brut − Amort &amp; Prov). Sans recalcul, elle part
 * entièrement à zéro — c'est le bug constaté en recette (retour-SIRYF affichant
 * des montants nets à 0).</p>
 *
 * <p>Ce test prouve que le {@link ExcelReader} résout correctement les chaînes
 * de formules, y compris les sous-totaux qui sont eux-mêmes des formules
 * ({@code =SUM(D9:D15)}, {@code =+D8+D16}).</p>
 */
class ExcelReaderFormulaTest {

    private final ExcelReader reader = new ExcelReader();

    private Workbook openSource() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/source-SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/source-SIGIMF.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    /** Lit le bilan avec skipBlankRows pour couvrir les deux blocs ACTIF + PASSIF. */
    private SheetTable readBilan(Workbook wb) {
        return reader.readSheet(wb, "M.I.BILAN", SheetLayout.defaults().skippingBlankRows());
    }

    /**
     * Parse un montant lu par le reader (peut contenir un signe € et des
     * espaces insécables du format comptable Excel).
     */
    private double parseMontant(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        // retire le signe € (Unicode ou texte), les espaces insécables, et les espaces normaux
        String cleaned = raw.replace("\u20AC", "")
                .replaceAll("[\\s\\u00A0\\u202F]+", "")
                .replace(",", ".")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals("€")) return 0;
        return Double.parseDouble(cleaned);
    }

    // ──────────────────────────────────────────────────────────────────
    // Test principal : le vrai bilan, 143 lignes, formules résolues
    // ──────────────────────────────────────────────────────────────────

    @Test
    void le_vrai_bilan_recalcule_montant_net_grace_aux_formules() throws Exception {
        try (Workbook wb = openSource()) {
            SheetTable table = readBilan(wb);

            assertFalse(table.rows().isEmpty(), "le bilan doit contenir des lignes");

            int totalLignes = 0;
            int formulesResolues = 0;

            for (Map<String, String> row : table.rows()) {
                String code = row.get("CODE");
                if (code == null || code.isEmpty()) continue;

                String mbRaw = row.get("Montant brut");
                String apRaw = row.get("Amortissement & Provisions");
                String mnRaw = row.get("Montant net");

                // Certaines lignes (notes, sections) n'ont pas de montants
                if (mbRaw == null && mnRaw == null) continue;

                double mb = parseMontant(mbRaw);
                double ap = parseMontant(apRaw);
                double mn = parseMontant(mnRaw);

                // Vérification : Montant net = Montant brut − Amort & Prov
                double attendu = mb - ap;
                totalLignes++;

                if (Math.abs(attendu) > 0.01 || Math.abs(mn) > 0.01) {
                    // Cette ligne a des montants significatifs → vérifier le calcul
                    assertEquals(attendu, mn, 1.0,
                            code + " : Montant net (" + mn + ") ≠ Montant brut (" + mb
                                    + ") − Amort & Prov (" + ap + ")");
                    formulesResolues++;
                }
            }

            assertTrue(totalLignes >= 130,
                    "au moins 130 lignes avec des montants dans le bilan (ACTIF+PASSIF), trouvé " + totalLignes);
            assertTrue(formulesResolues >= 100,
                    "au moins 100 lignes où Montant net est vérifié, trouvé " + formulesResolues);

            System.out.println("[M.I.BILAN] " + totalLignes + " lignes vérifiées, "
                    + formulesResolues + " avec montants significatifs");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Cas critiques : sous-totaux dont D est lui-même une formule
    // ──────────────────────────────────────────────────────────────────

    @Test
    void le_sous_total_actif_1_est_recalcule() throws Exception {
        // M.I.B.A.1 : D = +D8+D16 (chaîne de formules), E = valeur, F = D-E
        assertCode("M.I.B.A.1");
    }

    @Test
    void le_sous_total_actif_2_est_recalcule() throws Exception {
        // M.I.B.A.2 : D = D34+D41, E = valeur, F = D-E
        assertCode("M.I.B.A.2");
    }

    @Test
    void le_total_actif_est_recalcule() throws Exception {
        // M.I.B.A.TA : D = +D7+D33+D46+D59 (total le plus profond), F = D-E
        assertCode("M.I.B.A.TA");
    }

    @Test
    void le_total_passif_est_recalcule() throws Exception {
        // M.I.B.P.TP : D = D79+D99+D117+D131, F = D-E
        assertCode("M.I.B.P.TP");
    }

    // ──────────────────────────────────────────────────────────────────
    // Vérification du NON-ZERO sur des lignes clés
    // ──────────────────────────────────────────────────────────────────

    @Test
    void les_montants_net_ne_sont_jamais_tous_a_zero() throws Exception {
        try (Workbook wb = openSource()) {
            SheetTable table = readBilan(wb);

            long lignesNonZero = table.rows().stream()
                    .filter(r -> r.get("CODE") != null && !r.get("CODE").isEmpty())
                    .map(r -> parseMontant(r.get("Montant net")))
                    .filter(v -> Math.abs(v) > 0.01)
                    .count();

            assertTrue(lignesNonZero >= 50,
                    "au moins 50 lignes avec Montant net ≠ 0 (preuve que les formules sont recalculées), "
                            + "trouvé " + lignesNonZero);

            System.out.println("[M.I.BILAN] " + lignesNonZero + " lignes avec Montant net ≠ 0");
        }
    }

    @Test
    void les_montants_brut_ne_sont_jamais_tous_a_zero() throws Exception {
        try (Workbook wb = openSource()) {
            SheetTable table = readBilan(wb);

            long lignesNonZero = table.rows().stream()
                    .filter(r -> r.get("CODE") != null && !r.get("CODE").isEmpty())
                    .map(r -> parseMontant(r.get("Montant brut")))
                    .filter(v -> Math.abs(v) > 0.01)
                    .count();

            assertTrue(lignesNonZero >= 50,
                    "au moins 50 lignes avec Montant brut ≠ 0 (formules SUM/référence résolues), "
                            + "trouvé " + lignesNonZero);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Régression : pas de zéro là où il y a des données
    // ──────────────────────────────────────────────────────────────────

    @Test
    void la_colonne_montant_net_n_est_jamais_zero_quand_montant_brut_est_non_zero() throws Exception {
        try (Workbook wb = openSource()) {
            SheetTable table = readBilan(wb);

            int violations = 0;
            for (Map<String, String> row : table.rows()) {
                String code = row.get("CODE");
                if (code == null || code.isEmpty()) continue;

                double mb = parseMontant(row.get("Montant brut"));
                double mn = parseMontant(row.get("Montant net"));

                // Si Montant brut est non-zéro, Montant net ne devrait pas être zéro
                // (sauf si Amort & Prov = Montant brut exactement, ce qui est rare)
                if (Math.abs(mb) > 1000 && Math.abs(mn) < 0.01) {
                    violations++;
                    System.out.println("[BUG] " + code + " : brut=" + mb + " mais net=0");
                }
            }

            assertEquals(0, violations,
                    violations + " ligne(s) avec Montant brut non-zéro mais Montant net = 0 "
                            + "(formule non recalculée ?)");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Utilitaire
    // ──────────────────────────────────────────────────────────────────

    private void assertCode(String code) throws Exception {
        try (Workbook wb = openSource()) {
            SheetTable table = readBilan(wb);

            Map<String, String> row = table.rows().stream()                    .filter(r -> code.equals(r.get("CODE")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Code " + code + " absent du bilan"));

            double mb = parseMontant(row.get("Montant brut"));
            double ap = parseMontant(row.get("Amortissement & Provisions"));
            double mn = parseMontant(row.get("Montant net"));

            assertNotEquals(0, mb, 1.0, code + " : Montant brut ne doit pas être zéro");
            assertEquals(mb - ap, mn, 1.0,
                    code + " : Montant net (" + mn + ") ≠ Montant brut (" + mb
                            + ") − Amort & Prov (" + ap + ")");

            System.out.println("[OK] " + code + " : brut=" + mb + " − amort=" + ap + " = net=" + mn);
        }
    }
}
