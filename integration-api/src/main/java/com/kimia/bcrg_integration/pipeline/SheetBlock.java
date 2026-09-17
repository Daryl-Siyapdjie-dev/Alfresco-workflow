package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.SheetMapper;

/**
 * Une <strong>tranche</strong> de feuille à lire : sa forme (où est l'en-tête, comment sont agencées
 * les lignes) et le mapper qui la transforme en items.
 * <p>La plupart des feuilles SIGIMF n'en comportent qu'une. Certaines juxtaposent deux tableaux de
 * colonnes <em>différentes</em> qui alimentent pourtant le même envoi : M.XV.RECAP fait suivre son
 * récapitulatif d'un tableau « Couverture Géographique » dont les colonnes remplissent d'autres
 * champs du même DTO. Un {@link SheetJob} porte donc une liste de blocs, dont les items sont
 * concaténés dans un seul payload — un envoi par feuille, comme pour les autres.
 *
 * @param layout forme du bloc pour le lecteur (marqueur d'en-tête, en-tête multi-lignes, lignes vides)
 * @param mapper transformation des lignes de ce bloc en items typés
 * @param <I>    type d'item CIBLE, commun à tous les blocs d'une même feuille
 */
public record SheetBlock<I>(SheetLayout layout, SheetMapper<I> mapper) {

    public SheetBlock {
        if (layout == null) throw new IllegalArgumentException("Forme de bloc obligatoire");
        if (mapper == null) throw new IllegalArgumentException("Mapper de bloc obligatoire");
    }
}
