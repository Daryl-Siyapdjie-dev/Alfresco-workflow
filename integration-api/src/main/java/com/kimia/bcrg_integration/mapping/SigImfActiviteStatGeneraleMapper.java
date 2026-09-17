package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfActiviteStatGenerale;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code M.IX.SG} (données sur les activités et statistiques générales) → items
 * {@link SigImfActiviteStatGenerale} (endpoint {@code /api/sigimf/m9sg}).
 *
 * <p><strong>Ce n'est pas un tableau, c'est un formulaire de dix postes.</strong> Toutes les autres
 * feuilles ont des colonnes qui gardent le même sens du haut en bas ; ici, les trois colonnes de
 * détail (F, G, H) changent de signification tous les trois ou quatre rangs, et l'intitulé d'un poste
 * est tantôt sur sa propre ligne, tantôt dans le bloc de titres qui le précède. D'où deux règles
 * propres à cette feuille, et à elle seule.
 *
 * <p><strong>Règle 1 — l'intitulé.</strong> C'est la colonne {@code INDICATEURS} quand elle porte
 * autre chose que la légende «&nbsp;TOTAL&nbsp;» ; sinon, c'est le dernier libellé rencontré dans la
 * colonne du total, qui sert alors de titre au poste suivant (le template écrit
 * «&nbsp;Nombre total de membres/clients&nbsp;» au-dessus de la ligne qui porte le code).
 *
 * <p><strong>Règle 2 — les six dispositions.</strong> Les colonnes de détail se lisent différemment
 * selon le poste, et le template le dit lui-même dans ses sous-titres :
 * <table border="1">
 *   <caption>Disposition des colonnes de détail, par code</caption>
 *   <tr><th>Codes</th><th>Colonne F</th><th>Colonne G</th><th>Colonne H</th></tr>
 *   <tr><td>.1</td><td>Total</td><td>Agences ou caisses de base</td><td>Points de service</td></tr>
 *   <tr><td>.2 .3 .4</td><td>Individuel</td><td>Groupes</td><td>Personnes</td></tr>
 *   <tr><td>.5</td><td>Individuel</td><td colspan="2">Groupement (G et H fusionnées)</td></tr>
 *   <tr><td>.6 .9 .10</td><td colspan="3">une seule valeur (F à H fusionnées)</td></tr>
 *   <tr><td>.7</td><td colspan="3">un pourcentage</td></tr>
 *   <tr><td>.8</td><td>Femmes</td><td colspan="2">Hommes (G et H fusionnées)</td></tr>
 * </table>
 * Ces <strong>six</strong> dispositions sont vraisemblablement ce que désigne le champ
 * {@code typeColumn} du DTO, dont l'énumération compte elle aussi six valeurs (A…F) — et qui ne peut
 * pas être la colonne {@code ELEMENT} de la feuille, laquelle va de A à J. <strong>Hypothèse non
 * confirmée</strong> : {@code typeColumn} n'est donc pas renseigné tant que la BCRG ne l'a pas
 * tranché, et {@link Formes#lettre()} tient la correspondance prête.
 *
 * <p><strong>Écart assumé</strong> : quand G et H sont fusionnées, la valeur unique alimente
 * {@code groupement} — le mot qu'emploie le template pour ce qui couvre les deux sous-colonnes.
 * Pour le poste .8, ce même emplacement porte «&nbsp;Hommes&nbsp;», que le DTO ne nomme pas :
 * point à porter à la matrice de correspondance. {@code numeroLigne} n'a aucune source.
 */
@Component
public class SigImfActiviteStatGeneraleMapper implements SheetMapper<SigImfActiviteStatGenerale> {

    static final String CODE = "CODE";
    static final String ELEMENT = "ELEMENT";
    static final String INDICATEURS = "INDICATEURS";

    /** Colonne D : le total du poste, ou — sur les lignes sans code — l'intitulé du poste suivant. */
    static final String TOTAL = "col3";

    /**
     * Les trois colonnes de détail, nommées par la <strong>deuxième ligne d'en-tête</strong>.
     * <p>Ces intitulés sont ceux du poste .1 — c'est lui qui occupe la hauteur d'en-tête. Ils ne
     * décrivent donc <em>que</em> ce poste : plus bas, les mêmes colonnes portent « Individuel »,
     * « Groupes », « Femmes »… selon la disposition. On les garde comme <em>noms de colonne</em>,
     * pas comme sens : le sens, c'est {@link Formes} qui le donne.
     * <p>Lire l'en-tête sur deux lignes n'est pas cosmétique : à hauteur de la première, G et H sont
     * vides (la cellule fusionnée « INSCRIRE LE DETAIL… » ne remplit que F), et le lecteur élague
     * les colonnes vides de fin — les deux dernières colonnes de la feuille disparaîtraient.
     */
    static final String DETAIL = "Total";
    static final String DETAIL_2 = "Agences ou Caisses de base";
    static final String DETAIL_3 = "Points de service";

    /** Légende de la colonne du total, à ne pas confondre avec un intitulé de poste. */
    private static final String LEGENDE_TOTAL = "TOTAL";

    /** Les six dispositions des colonnes de détail, et le poste qui les emploie. */
    enum Formes {
        TOTAL_AGENCES_POINTS_DE_SERVICE('A'),
        INDIVIDUEL_GROUPES_PERSONNES('B'),
        INDIVIDUEL_GROUPEMENT('C'),
        VALEUR_UNIQUE('D'),
        POURCENTAGE('E'),
        FEMMES_HOMMES('F');

        private final char lettre;

        Formes(char lettre) {
            this.lettre = lettre;
        }

        /**
         * Lettre correspondante de l'énumération {@code typeColumn} du DTO — <strong>hypothèse</strong>,
         * prête à être branchée le jour où la BCRG confirme que les deux se correspondent.
         */
        char lettre() {
            return lettre;
        }
    }

    private static final Map<String, Formes> FORME_PAR_CODE = new LinkedHashMap<>();

    static {
        FORME_PAR_CODE.put("M.IX.SG.1", Formes.TOTAL_AGENCES_POINTS_DE_SERVICE);
        FORME_PAR_CODE.put("M.IX.SG.2", Formes.INDIVIDUEL_GROUPES_PERSONNES);
        FORME_PAR_CODE.put("M.IX.SG.3", Formes.INDIVIDUEL_GROUPES_PERSONNES);
        FORME_PAR_CODE.put("M.IX.SG.4", Formes.INDIVIDUEL_GROUPES_PERSONNES);
        FORME_PAR_CODE.put("M.IX.SG.5", Formes.INDIVIDUEL_GROUPEMENT);
        FORME_PAR_CODE.put("M.IX.SG.6", Formes.VALEUR_UNIQUE);
        FORME_PAR_CODE.put("M.IX.SG.7", Formes.POURCENTAGE);
        FORME_PAR_CODE.put("M.IX.SG.8", Formes.FEMMES_HOMMES);
        FORME_PAR_CODE.put("M.IX.SG.9", Formes.VALEUR_UNIQUE);
        FORME_PAR_CODE.put("M.IX.SG.10", Formes.VALEUR_UNIQUE);
    }

    @Override
    public List<SigImfActiviteStatGenerale> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), ELEMENT, INDICATEURS, DETAIL);
        List<SigImfActiviteStatGenerale> items = new ArrayList<>();
        String intituleEnAttente = "";

        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            String colonneTotal = cols.get(row, TOTAL);

            if (!Codes.estCodeDeLigne(code)) {
                // ligne de titres : elle annonce l'intitulé du poste qui suit
                if (!colonneTotal.isBlank() && !estNombre(colonneTotal)) {
                    intituleEnAttente = colonneTotal;
                }
                continue;
            }

            Formes forme = FORME_PAR_CODE.get(code);
            if (forme == null) {
                throw new MappingException("Code inconnu dans M.IX.SG : « " + code
                        + " » — la feuille est un formulaire à dix postes fixes, pas une liste");
            }

            SigImfActiviteStatGenerale item = new SigImfActiviteStatGenerale()
                    .code(code)
                    .element(cols.get(row, ELEMENT))
                    .indicateurs(intitule(cols.get(row, INDICATEURS), colonneTotal, intituleEnAttente));

            if (estNombre(colonneTotal)) {
                item.total(Numbers.parseMontantOuRenvoi(colonneTotal, TOTAL));
            }
            Double premier = Numbers.parseMontantOuRenvoi(cols.get(row, DETAIL), DETAIL);
            Double deuxieme = Numbers.parseMontantOuRenvoi(cols.get(row, DETAIL_2), DETAIL_2);
            Double troisieme = Numbers.parseMontantOuRenvoi(cols.get(row, DETAIL_3), DETAIL_3);

            item.details(premier);
            switch (forme) {
                case TOTAL_AGENCES_POINTS_DE_SERVICE, INDIVIDUEL_GROUPES_PERSONNES -> {
                    item.groupes(deuxieme);
                    item.personnes(troisieme);
                }
                // G et H fusionnées : une seule valeur, qui couvre les deux sous-colonnes
                case INDIVIDUEL_GROUPEMENT, FEMMES_HOMMES -> item.groupement(deuxieme);
                // F à H fusionnées : le premier détail porte tout
                case VALEUR_UNIQUE, POURCENTAGE -> { /* rien de plus */ }
            }
            items.add(item);
        }
        return items;
    }

    /** Règle 1 : l'intitulé du poste, sur sa propre ligne ou hérité du bloc de titres qui précède. */
    private static String intitule(String indicateurs, String colonneTotal, String enAttente) {
        if (!indicateurs.isBlank() && !LEGENDE_TOTAL.equalsIgnoreCase(indicateurs.trim())) {
            return indicateurs;
        }
        // le poste .1 porte son intitulé dans la colonne du total, sur sa propre ligne
        if (!colonneTotal.isBlank() && !estNombre(colonneTotal)) {
            return colonneTotal;
        }
        return enAttente;
    }

    /** Vrai si la cellule porte un nombre (et non un intitulé) — la colonne D sert aux deux. */
    private static boolean estNombre(String valeur) {
        String s = valeur.trim();
        if (s.isEmpty()) return false;
        return s.chars().noneMatch(Character::isLetter);
    }
}
