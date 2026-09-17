package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.AutresNormesPrudentielles;
import com.kimia.bcrg_integration.client.dto.CalculFondsPropresNets;
import com.kimia.bcrg_integration.client.dto.CalculRatioSolvabiliteImf;
import com.kimia.bcrg_integration.client.dto.CoefficientObservationLiquiditeImmediate;
import com.kimia.bcrg_integration.client.dto.ConformiteNormesPrudentielles;
import com.kimia.bcrg_integration.client.dto.DivisionRisques;
import com.kimia.bcrg_integration.client.dto.ImfCollectantDepot;
import com.kimia.bcrg_integration.client.dto.RisquesCredits;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les 12 feuilles de PRUD, lues sur le <strong>vrai template</strong>
 * ({@code src/test/resources/samples/PRUD.xlsx}, copie conforme du fichier qui viendra d'Alfresco).
 * <p>Des données synthétiques ne prouveraient ici presque rien : ce qui casse sur ce fichier, ce sont
 * ses irrégularités réelles — deux graphies du marqueur d'en-tête, un second bloc dans PRUD_02, des
 * renvois de cellule au lieu de montants, une note de bas de feuille dans la colonne des codes.
 */
class PrudMappersTest {

    private final ExcelReader reader = new ExcelReader();
    private final SheetLayout forme = SheetLayout.marker("N°CODE").skippingBlankRows();

    private Workbook wb;

    @BeforeEach
    void ouvrir() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/PRUD.xlsx");
        assertNotNull(in, "Échantillon samples/PRUD.xlsx absent du classpath de test");
        wb = WorkbookFactory.create(in);
    }

    @AfterEach
    void fermer() throws Exception {
        if (wb != null) wb.close();
    }

    private SheetTable lire(String feuille) {
        return reader.readSheet(wb, feuille, forme);
    }

    @Test
    void ecnp_transmet_les_treize_normes_et_ignore_les_titres_de_section() {
        List<ConformiteNormesPrudentielles> items = new PrudEcnpMapper().map(lire("ECNP"));

        assertEquals(13, items.size(), "4 titres de section (« NORMES DE SOLVABILITE »…) sont écartés");
        ConformiteNormesPrudentielles premier = items.get(0);
        assertEquals("RA0_001", premier.getCode());
        assertEquals("1.1", premier.getNumero());
        assertTrue(premier.getNorme().startsWith("Ratio de Fonds propres"), premier.getNorme());
        assertEquals(0.065, premier.getNiveauRepecter(), 1e-9);
        assertEquals("RA0_013", items.get(12).getCode());
    }

    @Test
    void ecnp_traite_une_consigne_de_remplissage_comme_une_case_vide() {
        List<ConformiteNormesPrudentielles> items = new PrudEcnpMapper().map(lire("ECNP"));

        // RA0_007 porte « Nombre de dossiers en infraction(PRUD_04) » dans la colonne du niveau observé
        ConformiteNormesPrudentielles ligne = items.stream()
                .filter(i -> "RA0_007".equals(i.getCode())).findFirst().orElseThrow();
        assertNull(ligne.getNiveauObserve(), "une consigne n'est pas un taux : la case est vide");
        assertEquals(0.1, ligne.getNiveauRepecter(), 1e-9, "le niveau à respecter, lui, est un nombre");
    }

    @Test
    void fpn_lit_les_trente_trois_postes_et_neutralise_les_renvois_de_cellule() {
        List<CalculFondsPropresNets> items = new PrudFpnMapper().map(lire("FPN"));

        assertEquals(33, items.size(), "FP0_000 à FP0_032, lignes vides internes traversées");
        CalculFondsPropresNets capital = items.get(1);
        assertEquals("FP0_001", capital.getCode());
        assertEquals("572", capital.getNumCompte());
        // FP0_010 porte « IGEC_18: AT13 » dans la colonne Montant : un renvoi, pas un montant
        CalculFondsPropresNets renvoi = items.stream()
                .filter(i -> "FP0_010".equals(i.getCode())).findFirst().orElseThrow();
        assertNull(renvoi.getMontant());
        // la pondération, elle, est un vrai nombre là où le template en porte un
        CalculFondsPropresNets pondere = items.stream()
                .filter(i -> "FP0_005".equals(i.getCode())).findFirst().orElseThrow();
        assertEquals(0.15, pondere.getPonderation(), 1e-9);
    }

    @Test
    void apr_ignore_la_ligne_de_numerotation_des_colonnes() {
        List<RisquesCredits> items = new PrudAprMapper().map(lire("APR"));

        assertEquals(16, items.size(), "RC0_001 à RC0_016 ; « (1) | (2) | (3) = 1 x 2 » n'en est pas");
        assertEquals("RC0_001", items.get(0).getCode());
        assertEquals("102", items.get(0).getNumCompte());
        assertEquals(0.25, items.get(1).getCoefficientPonderation(), 1e-9);
        assertFalse(items.stream().anyMatch(i -> i.getPoste() != null && i.getPoste().startsWith("(")),
                "aucune ligne de service ne doit passer");
    }

    @Test
    void prud_01_lit_les_ratios_et_leur_reference() {
        List<CalculRatioSolvabiliteImf> items = new PrudRatioSolvabiliteMapper().map(lire("PRUD_01"));

        assertEquals(9, items.size());
        assertEquals("RA0_001", items.get(0).getCode());
        assertEquals("a x 100 / e", items.get(0).getReference());
        assertEquals("APR_04", items.get(8).getCode());
    }

    /** Le cas qui justifiait à lui seul de lire le vrai fichier : deux blocs, deux graphies du marqueur. */
    @Test
    void prud_02_enchaine_ses_deux_blocs_dans_un_seul_envoi() {
        List<CoefficientObservationLiquiditeImmediate> items =
                new PrudLiquiditeImmediateMapper().map(lire("PRUD_02"));

        assertEquals(11, items.size(), "7 lignes du numérateur + 4 du dénominateur");
        assertEquals("EPR_0800", items.get(0).getCode(), "premier bloc : « Numérateur - Réalisables »");
        assertEquals("EPR_0900", items.get(10).getCode(), "dernier bloc : « Dénominateur : Exigibles »");
        assertFalse(items.stream().anyMatch(i -> i.getLibelle() != null && i.getLibelle().contains("GNF")),
                "les lignes d'unité (« GNF », « 1 ») ne portent pas de code : elles restent à quai");
    }

    @Test
    void prud_03_se_lit_malgre_un_marqueur_ecrit_avec_une_espace() {
        // en-tête « N° CODE » ici, « N°CODE » ailleurs : le lecteur ignore les espaces
        assertEquals(23, new PrudEmploisStablesMapper().map(lire("PRUD_03")).size());
    }

    @Test
    void prud_04_transmet_les_lignes_pre_codees_de_la_liste() {
        List<DivisionRisques> items = new PrudDivisionRisquesMapper().map(lire("PRUD_04"));

        assertEquals(15, items.size(), "13 bénéficiaires pré-codés + les 2 lignes de total");
        assertEquals("EPR_0110", items.get(0).getCode());
        assertEquals("1", items.get(0).getNumero());
        assertEquals("TE0_0270", items.get(14).getCode());
        assertFalse(items.stream().anyMatch(i -> "1".equals(i.getCode())),
                "la ligne de numérotation des colonnes n'est pas un item");
    }

    @Test
    void prud_05_neutralise_les_renvois_des_trois_colonnes_de_montant() {
        var items = new PrudConcoursMandatairesMapper().map(lire("PRUD_05"));

        assertEquals(5, items.size());
        var premier = items.get(0);
        assertEquals("EPR_0600", premier.getCode());
        assertNull(premier.getMontantConcours(), "« IGEC17_AE17 » est un renvoi, pas un montant");
        assertNull(premier.getProvisionsComptabilisées());
    }

    @Test
    void prud_06_ecarte_la_note_de_bas_de_feuille_de_la_colonne_des_codes() {
        List<AutresNormesPrudentielles> items = new PrudNormeMapper<>(
                "AUTRES NORMES PRUDENTIELLES",
                (code, libelle, montant, seuil, norme) -> new AutresNormesPrudentielles()
                        .code(code).libelle(libelle).montant(montant).seuilObserve(seuil).norme(norme))
                .map(lire("PRUD_06"));

        assertEquals(19, items.size(), "« *EP: Etat Périodique » n'est pas un code de ligne");
        assertEquals("EPR_1510", items.get(0).getCode());
        assertFalse(items.stream().anyMatch(i -> i.getCode().contains(" ")));
    }

    @Test
    void imao_icd_lit_les_quarante_six_ratios_harmonises() {
        List<ImfCollectantDepot> items = new PrudNormeMapper<>(
                "RATIOS PRUDENTIELS HARMONISES",
                (code, libelle, montant, seuil, norme) -> new ImfCollectantDepot()
                        .code(code).libelle(libelle).montant(montant).seuilObserve(seuil).norme(norme))
                .map(lire("IMAO_ICD"));

        assertEquals(46, items.size());
        assertEquals("ICD_001", items.get(0).getCode());
        assertEquals("ICD_046", items.get(45).getCode());
        // la norme reste du texte (« ≥10% ») : c'est bien un champ chaîne côté DTO
        assertTrue(items.stream().anyMatch(i -> i.getNorme() != null && i.getNorme().contains("10%")));
    }

    @Test
    void imao_incd_lit_ses_quarante_trois_ratios() {
        var items = new PrudNormeMapper<>(
                "RATIOS PRUDENTIELS HARMONISES",
                (code, libelle, montant, seuil, norme) -> new com.kimia.bcrg_integration.client.dto
                        .ImfNeCollectantDepot().code(code).libelle(libelle).montant(montant)
                        .seuilObserve(seuil).norme(norme))
                .map(lire("IMAO_INCD"));

        assertEquals(43, items.size());
        assertEquals("INCD_001", items.get(0).getCode());
    }

    @Test
    void fpnc_et_les_autres_feuilles_courtes_sont_completes() {
        assertEquals(9, new PrudFpncMapper().map(lire("FPNC")).size());
    }
}
