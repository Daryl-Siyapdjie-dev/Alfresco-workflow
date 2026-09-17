package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.excel.SheetTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper <strong>mutualisé</strong> des trois feuilles de PRUD qui listent des normes prudentielles
 * avec leur montant, leur seuil observé et la norme à respecter :
 * <ul>
 *   <li>{@code PRUD_06} — autres normes prudentielles → {@code /api/imf/prud/prud_06} ;</li>
 *   <li>{@code IMAO_ICD} — IMF collectant des dépôts → {@code /api/imf/prud/imao_icd} ;</li>
 *   <li>{@code IMAO_INCD} — IMF ne collectant pas de dépôts → {@code /api/imf/prud/imao_incd}.</li>
 * </ul>
 * Même disposition, même sens des colonnes, mais <strong>trois DTO distincts</strong> aux champs
 * identiques ({@code code, libelle, montant, seuilObserve, norme}) : d'où un mapper générique et une
 * {@link Fabrique} par feuille, plutôt que trois copies de la même boucle. Seul l'intitulé de la
 * colonne de libellé change (« AUTRES NORMES PRUDENTIELLES » / « RATIOS PRUDENTIELS HARMONISES »),
 * il est donc passé au constructeur.
 *
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Les titres de section
 * (« ADEQUATION DES FONDS PROPRES », « QUALITE DES ACTIFS ») en portent un eux aussi dans ces
 * feuilles — ils partent donc avec le reste, ce qui est cohérent : SIRYF raisonne par code de ligne
 * et c'est lui qui connaît la nomenclature.
 *
 * <p><strong>Écart assumé</strong> : la colonne « N° » de ces feuilles est une <em>numérotation</em> de
 * section (« 1 », « 1.1 », « 2.1.1 »), pas un numéro de compte. Le seul champ texte libre du DTO
 * étant {@code numCompte}, l'y verser inventerait un sens : elle n'est donc <strong>pas</strong>
 * transmise, et le point part à la matrice de correspondance.
 *
 * <p>Les colonnes de montant du template portent des renvois (« FPN_F56 », « SOLDE COMPTE 21 ») :
 * consignes de remplissage, traitées comme des cases vides ({@link Numbers#parseMontantOuRenvoi}).
 * La colonne « Norme » reste du <em>texte</em> (« ≥10% », « ≤ 10% ») — c'est bien un champ chaîne
 * côté DTO.
 *
 * @param <T> type d'item produit (un par endpoint)
 */
public class PrudNormeMapper<T> implements SheetMapper<T> {

    /** Construit l'item d'une ligne — une implémentation par DTO cible. */
    @FunctionalInterface
    public interface Fabrique<T> {
        T creer(String code, String libelle, Double montant, Double seuilObserve, String norme);
    }

    static final String CODE = "N°CODE";
    static final String MONTANT = "Montant";
    static final String SEUIL = "Seuil observé";
    static final String NORME = "Norme";

    private final String colonneLibelle;
    private final Fabrique<T> fabrique;

    public PrudNormeMapper(String colonneLibelle, Fabrique<T> fabrique) {
        this.colonneLibelle = colonneLibelle;
        this.fabrique = fabrique;
    }

    @Override
    public List<T> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), colonneLibelle, MONTANT, SEUIL, NORME);
        List<T> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // ligne de service, ou note de bas de feuille
            items.add(fabrique.creer(
                    code,
                    cols.get(row, colonneLibelle),
                    Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT),
                    Numbers.parseMontantOuRenvoi(cols.get(row, SEUIL), SEUIL),
                    cols.get(row, NORME)));
        }
        return items;
    }
}
