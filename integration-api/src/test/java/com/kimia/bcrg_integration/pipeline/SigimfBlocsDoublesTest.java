package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetTable;
import com.kimia.bcrg_integration.mapping.SigImfBilanMapper;
import com.kimia.bcrg_integration.mapping.SigImfCompteResultatMapper;
import com.kimia.bcrg_integration.mapping.SigImfImpayeRatiosMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trois feuilles SIGIMF empilent deux tableaux : {@code M.I.BILAN} (actif puis passif),
 * {@code M.II.RESULTAT} (charges puis produits) et {@code M.IV.IMPAYE} (impayés puis ratios PAR).
 * Chacune doit être déclarée en <strong>deux blocs</strong>, faute de quoi seule la première moitié
 * part — un défaut qu'aucune réponse de l'API ne signale, puisqu'un demi-bilan lui est valide.
 *
 * <p>Le contrôle porte sur le <em>nombre de lignes lues</em> comparé au nombre de codes du modèle
 * réel ({@code samples/SIGIMF.xlsx}), et non sur les montants : les colonnes de montant du template
 * contiennent des formules Excel, pas des valeurs.
 */
class SigimfBlocsDoublesTest {

    private final ExcelReader reader = new ExcelReader();
    private final SigimfJobRegistry registry =
            new SigimfJobRegistry((uri, body) -> ResponseEntity.ok("x"));

    private Workbook ouvrirTemplate() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/SIGIMF.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    /** Lignes portant un code, bloc par bloc, telles que le job les lira. */
    private List<Integer> lignesParBloc(Workbook wb, String feuille) {
        return registry.forSheet(feuille).blocks().stream()
                .map(bloc -> reader.readSheet(wb, feuille, bloc.layout()))
                .map(table -> (int) table.rows().stream()
                        .filter(r -> !premiereColonne(table, r).isBlank())
                        .count())
                .toList();
    }

    private String premiereColonne(SheetTable table, Map<String, String> row) {
        String valeur = row.get(table.headers().get(0));
        return valeur == null ? "" : valeur;
    }

    @Test
    void le_bilan_lit_l_actif_et_le_passif() throws Exception {
        try (Workbook wb = ouvrirTemplate()) {
            assertEquals(List.of(70, 71), lignesParBloc(wb, "M.I.BILAN"),
                    "le modèle porte 70 lignes d'actif et 71 de passif");
        }
    }

    @Test
    void le_compte_de_resultat_lit_les_charges_et_les_produits() throws Exception {
        try (Workbook wb = ouvrirTemplate()) {
            assertEquals(List.of(52, 45), lignesParBloc(wb, "M.II.RESULTAT"),
                    "le modèle porte 52 lignes de charges et 45 de produits");
        }
    }

    @Test
    void les_impayes_lisent_le_tableau_et_les_ratios() throws Exception {
        try (Workbook wb = ouvrirTemplate()) {
            assertEquals(List.of(8, 3), lignesParBloc(wb, "M.IV.IMPAYE"),
                    "8 tranches d'impayés, puis les 3 ratios PAR 1/30/90");
        }
    }

    /**
     * Le côté d'une ligne n'a pas de colonne source : c'est le bloc d'où elle vient qui le dit.
     * Le mapper doit donc porter le booléen, sans quoi actif et passif arrivent indiscernables.
     */
    @Test
    void le_bloc_porte_le_cote_de_la_ligne() {
        SheetTable passif = new SheetTable("M.I.BILAN", null,
                List.of("CODE", "N° compte", "PASSIF", "Montant brut",
                        "Amortissement & Provisions", "Montant net"),
                List.of(Map.of("CODE", "M.I.B.P.1611", "N° compte", "1611", "PASSIF", "Très court terme",
                        "Montant brut", "1 000", "Amortissement & Provisions", "", "Montant net", "")));

        var items = new SigImfBilanMapper(SigImfBilanMapper.COL_PASSIF, false).map(passif);
        assertEquals(1, items.size());
        assertEquals(Boolean.FALSE, items.get(0).getActif());
        assertEquals("Très court terme", items.get(0).getLibelle());
        assertEquals(1000.0, items.get(0).getMontantBrut(), 1e-9);

        SheetTable produits = new SheetTable("M.II.RESULTAT", null,
                List.of("CODE", "N° compte", "PRODUITS", "Montant"),
                List.of(Map.of("CODE", "M.II.R.P.7011", "N° compte", "7011",
                        "PRODUITS", "Intérêts sur crédits", "Montant", "2 500")));

        var produitsMappes =
                new SigImfCompteResultatMapper(SigImfCompteResultatMapper.COL_PRODUITS, true).map(produits);
        assertEquals(Boolean.TRUE, produitsMappes.get(0).getProduct());
        assertEquals(2500.0, produitsMappes.get(0).getMontant(), 1e-9);
    }

    /**
     * Les trois états de synthèse sont trimestriels — SIRYF refuse une fin de mois ordinaire
     * (« La date fournie n'est pas la fin du trimestre »). Sans ce rythme, le module tenterait
     * l'envoi douze fois par an et se ferait refuser huit fois.
     */
    @Test
    void les_etats_de_synthese_sont_trimestriels() {
        assertEquals(Periodicite.TRIMESTRIELLE, registry.periodicite("M.I.BILAN"));
        assertEquals(Periodicite.TRIMESTRIELLE, registry.periodicite("M.II.RESULTAT"));
        assertEquals(Periodicite.TRIMESTRIELLE, registry.periodicite("M.IV.IMPAYE"));
        assertEquals(Periodicite.MENSUELLE, registry.periodicite("M.V.PFC"));
    }

    /** Le second tableau de M.IV.IMPAYE a ses propres colonnes, dont la norme réglementaire. */
    @Test
    void les_ratios_portent_la_norme_reglementaire() {
        SheetTable ratios = new SheetTable("M.IV.IMPAYE", null,
                List.of("CODE", "ELEMENT", "Capital restant dû", "%", "Norme"),
                List.of(Map.of("CODE", "M.IV.IMP.10", "ELEMENT", "Retard de plus de 30 jours (PAR 30)",
                        "Capital restant dû", "12 000", "%", "0,08", "Norme", "0,05")));

        var items = new SigImfImpayeRatiosMapper().map(ratios);
        assertEquals(1, items.size());
        assertEquals(12000.0, items.get(0).getCapitalRestant(), 1e-9);
        assertEquals(0.08, items.get(0).getCapitalRestantPercent(), 1e-9);
        assertEquals(0.05, items.get(0).getNorme(), 1e-9);
        assertTrue(items.get(0).getElement().contains("PAR 30"));
    }
}
