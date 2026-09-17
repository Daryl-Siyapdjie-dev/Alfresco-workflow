package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.excel.SheetTable;

import java.util.List;

/**
 * Transforme une feuille Excel lue (côté SOURCE) en une liste d'items typés (côté CIBLE = DTO SYRIF).
 * <p>C'est le contrat de base du moteur de transformation (Lot 3) : chaque feuille du périmètre
 * (SIGIMF, PRUD, IGEC, FINS, …) a son propre mapper produisant le type d'{@code items} attendu par
 * son {@code DataModel…String}. L'abstraction reste valable quelle que soit la structure exacte de la
 * source ; seule l'implémentation du mapper change si la source diffère du template.
 *
 * @param <T> le type d'item CIBLE (ex. {@code SigImfInformationGenerale})
 */
public interface SheetMapper<T> {

    /** Construit les items CIBLE à partir des lignes de la feuille. */
    List<T> map(SheetTable sheet);
}
