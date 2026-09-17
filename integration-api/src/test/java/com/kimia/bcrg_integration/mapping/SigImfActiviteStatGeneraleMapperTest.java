package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfActiviteStatGenerale;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetLayout;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code M.IX.SG}, la seule feuille de SIGIMF qui soit un <strong>formulaire</strong> et non un
 * tableau. Lue sur un classeur <strong>rempli</strong> ({@code samples/SIGIMF-M9SG-rempli.xlsx}) dont
 * chaque cellule porte un nombre qui dit d'où il vient : centaine = le poste, unité = la colonne
 * (1 = total, 2 = F, 3 = G, 4 = H). Le poste 2 remplit donc D=201, F=202, G=203, H=204.
 * <p>C'est ce codage qui permet d'affirmer <em>quelle cellule alimente quel champ</em> — sur un
 * template vide, un mapping faux serait indiscernable d'un mapping juste.
 */
class SigImfActiviteStatGeneraleMapperTest {

    private final ExcelReader reader = new ExcelReader();
    /** Même forme que celle déclarée au registre : en-tête sur deux lignes, feuille aérée. */
    private final SheetLayout forme = SheetLayout.defaults().spanning(2).skippingBlankRows();

    private List<SigImfActiviteStatGenerale> lire(String ressource) throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream(ressource);
        assertNotNull(in, "Échantillon " + ressource + " absent du classpath de test");
        try (Workbook wb = WorkbookFactory.create(in)) {
            return new SigImfActiviteStatGeneraleMapper().map(reader.readSheet(wb, "M.IX.SG", forme));
        }
    }

    private Map<String, SigImfActiviteStatGenerale> parCode(List<SigImfActiviteStatGenerale> items) {
        return items.stream().collect(Collectors.toMap(
                SigImfActiviteStatGenerale::getCode, Function.identity(), (a, b) -> a));
    }

    @Test
    void transmet_les_dix_postes_du_formulaire() throws Exception {
        List<SigImfActiviteStatGenerale> items = lire("samples/SIGIMF-M9SG-rempli.xlsx");

        assertEquals(10, items.size(), "dix postes, ni plus (lignes de titres) ni moins");
        assertEquals("M.IX.SG.1", items.get(0).getCode());
        assertEquals("M.IX.SG.10", items.get(9).getCode());
        assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                items.stream().map(SigImfActiviteStatGenerale::getElement).map(String::trim).toList());
    }

    /**
     * Règle 1 : l'intitulé est sur la ligne du poste, ou hérité du bloc de titres qui la précède —
     * et la légende « TOTAL » de la colonne du total n'est jamais un intitulé.
     */
    @Test
    void reprend_l_intitule_qu_il_soit_sur_la_ligne_ou_dans_le_bloc_au_dessus() throws Exception {
        Map<String, SigImfActiviteStatGenerale> postes = parCode(lire("samples/SIGIMF-M9SG-rempli.xlsx"));

        // .1 porte son intitulé sur sa propre ligne
        assertTrue(postes.get("M.IX.SG.1").getIndicateurs().contains("institutions en opération"),
                postes.get("M.IX.SG.1").getIndicateurs());
        // .2 hérite du titre écrit au-dessus de lui
        assertTrue(postes.get("M.IX.SG.2").getIndicateurs().contains("membres/clients"),
                postes.get("M.IX.SG.2").getIndicateurs());
        // .8 aussi, deux blocs plus bas
        assertTrue(postes.get("M.IX.SG.8").getIndicateurs().contains("employés total"),
                postes.get("M.IX.SG.8").getIndicateurs());
        assertTrue(postes.values().stream().noneMatch(i -> "TOTAL".equalsIgnoreCase(i.getIndicateurs())),
                "« TOTAL » est la légende de la colonne, pas l'intitulé d'un poste");
    }

    /** Règle 2 : chaque disposition verse les colonnes de détail dans les bons champs. */
    @Test
    void chaque_disposition_verse_les_colonnes_dans_les_bons_champs() throws Exception {
        Map<String, SigImfActiviteStatGenerale> postes = parCode(lire("samples/SIGIMF-M9SG-rempli.xlsx"));

        // .2 — Individuel / Groupes / Personnes, avec un total
        SigImfActiviteStatGenerale membres = postes.get("M.IX.SG.2");
        assertEquals(201.0, membres.getTotal(), 1e-9, "colonne D");
        assertEquals(202.0, membres.getDetails(), 1e-9, "colonne F (Individuel)");
        assertEquals(203.0, membres.getGroupes(), 1e-9, "colonne G (Groupes)");
        assertEquals(204.0, membres.getPersonnes(), 1e-9, "colonne H (Personnes)");
        assertNull(membres.getGroupement(), "pas de colonne « Groupement » sur cette disposition");

        // .1 — Total / Agences / Points de service, sans total en colonne D (elle porte l'intitulé)
        SigImfActiviteStatGenerale institutions = postes.get("M.IX.SG.1");
        assertNull(institutions.getTotal(), "la colonne D porte ici l'intitulé, pas un nombre");
        assertEquals(102.0, institutions.getDetails(), 1e-9);
        assertEquals(103.0, institutions.getGroupes(), 1e-9);
        assertEquals(104.0, institutions.getPersonnes(), 1e-9);

        // .5 — Individuel / Groupement : G et H fusionnées, la valeur unique va dans groupement
        SigImfActiviteStatGenerale volume = postes.get("M.IX.SG.5");
        assertEquals(501.0, volume.getTotal(), 1e-9);
        assertEquals(502.0, volume.getDetails(), 1e-9);
        assertEquals(503.0, volume.getGroupement(), 1e-9);
        assertNull(volume.getGroupes());
        assertNull(volume.getPersonnes());

        // .6 — une seule valeur, F à H fusionnées
        SigImfActiviteStatGenerale dirigeants = postes.get("M.IX.SG.6");
        assertEquals(602.0, dirigeants.getDetails(), 1e-9);
        assertNull(dirigeants.getTotal());
        assertNull(dirigeants.getGroupes());
        assertNull(dirigeants.getGroupement());

        // .8 — Femmes / Hommes, G et H fusionnées
        SigImfActiviteStatGenerale employes = postes.get("M.IX.SG.8");
        assertEquals(801.0, employes.getTotal(), 1e-9);
        assertEquals(802.0, employes.getDetails(), 1e-9, "Femmes");
        assertEquals(803.0, employes.getGroupement(), 1e-9, "Hommes (emplacement fusionné)");
    }

    /** Les deux champs sans source restent vides tant que la BCRG ne les a pas tranchés. */
    @Test
    void les_champs_sans_source_ne_sont_pas_inventes() throws Exception {
        List<SigImfActiviteStatGenerale> items = lire("samples/SIGIMF-M9SG-rempli.xlsx");

        assertTrue(items.stream().allMatch(i -> i.getTypeColumn() == null),
                "typeColumn : hypothèse (6 dispositions ↔ 6 valeurs A–F) non confirmée");
        assertTrue(items.stream().allMatch(i -> i.getNumeroLigne() == null),
                "numeroLigne : aucune colonne source dans la feuille");
    }

    /** Sur le template vide, la structure tient toujours : dix postes, valeurs absentes. */
    @Test
    void lit_aussi_le_template_vide_sans_inventer_de_valeurs() throws Exception {
        List<SigImfActiviteStatGenerale> items = lire("samples/SIGIMF.xlsx");

        assertEquals(10, items.size());
        assertEquals("M.IX.SG.1", items.get(0).getCode());
        // le seul chiffre pré-imprimé du template est la norme des 10 % du poste .7
        SigImfActiviteStatGenerale pourcentage = parCode(items).get("M.IX.SG.7");
        assertEquals(0.1, pourcentage.getDetails(), 1e-9);
        assertNull(parCode(items).get("M.IX.SG.2").getDetails(), "aucun effectif saisi");
    }
}
