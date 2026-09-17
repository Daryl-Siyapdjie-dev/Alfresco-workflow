package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.excel.SheetTable;
import com.kimia.bcrg_integration.mapping.SheetMapper;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * <strong>Second tableau</strong> d'une feuille, d'un type différent du premier, à transmettre dans
 * la liste {@code items2} du même envoi.
 *
 * <p>Quatre feuilles de FINS empilent deux tableaux qui n'ont rien à voir l'un avec l'autre :
 * {@code FINS_09} liste les produits puis les charges, {@code FINS_12} les encours en souffrance puis
 * les provisions requises, {@code FINS_14} les charges de personnel puis le nombre d'employés,
 * {@code FINS_16} le capital restant dû puis le nombre. L'API le dit à sa façon : leur
 * {@code DataModel} porte un {@code items2} <em>typé</em> — {@code CompteResultatCharges},
 * {@code ProvisionCreditsSouffrance}… — là où les autres n'ont qu'un {@code String} sans usage.
 *
 * <p>À ne pas confondre avec {@link SheetBlock} : deux <em>blocs</em> sont deux tranches d'un même
 * tableau, dont les items sont concaténés (M.XV.RECAP, SITU_01) ; un <em>second tableau</em> est une
 * autre nature de données, qui part dans sa propre liste.
 *
 * <p>Le type de ses items ({@code I2}) n'apparaît pas dans l'interface : il est capté par
 * {@link #of} au moment du câblage, ce qui évite d'ajouter un paramètre de type à {@link SheetJob}
 * — et donc à tous les registres — pour une forme que quatre feuilles sur soixante-dix utilisent.
 *
 * @param <D> type du {@code DataModel} de la feuille
 */
public interface SecondTable<D> {

    /** Forme du second tableau pour le lecteur (son en-tête est plus bas dans la même feuille). */
    SheetLayout layout();

    /**
     * Mappe le second tableau et le dépose dans le payload déjà assemblé.
     *
     * @return nombre d'items du second tableau, à ajouter au compte de la feuille
     */
    int mapInto(SheetTable table, D payload);

    /**
     * Câble un second tableau : sa forme, son mapper, et le champ du {@code DataModel} qui le reçoit.
     * <p>Un tableau <strong>vide</strong> n'est pas déposé : {@code items2} reste à {@code null}.
     * SIRYF refuse un corps portant {@code "items2": []} (« Veuillez fournir les données à
     * traiter ») alors qu'il accepte le même corps avec {@code null}. La distinction vaut donc
     * aussi pour un second tableau bien réel, mais que le classeur n'a pas rempli.
     */
    static <I2, D> SecondTable<D> of(SheetLayout layout, SheetMapper<I2> mapper,
                                     BiConsumer<D, List<I2>> setItems2) {
        return new SecondTable<>() {
            @Override
            public SheetLayout layout() {
                return layout;
            }

            @Override
            public int mapInto(SheetTable table, D payload) {
                List<I2> items = mapper.map(table);
                if (!items.isEmpty()) {
                    setItems2.accept(payload, items);
                }
                return items.size();
            }
        };
    }
}
