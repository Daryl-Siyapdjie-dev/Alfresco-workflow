package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.TransmissionImf;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Assemblage des {@code DataModel…String} envoyés à SIRYF, mutualisé entre les registres de jobs
 * (un par fichier réglementaire). Les DataModel sont tous générés depuis la même spec OpenAPI :
 * ils partagent donc la même forme — {@code items} + {@code items2} + {@code transmission} — et les
 * mêmes pièges.
 */
final class Payloads {

    private Payloads() {}

    /** Assemble items + en-tête de transmission en un {@code DataModel…String} prêt à partir. */
    static <I, D> BiFunction<List<I>, TransmissionImf, D> assembler(Supplier<D> dataModelFactory,
                                                                    BiConsumer<D, List<I>> setItems,
                                                                    BiConsumer<D, TransmissionImf> setTransmission) {
        return (items, transmission) -> {
            D dm = dataModelFactory.get();
            setItems.accept(dm, items);
            setTransmission.accept(dm, transmission);
            neutraliserItems2(dm);
            return dm;
        };
    }

    /**
     * Vide le champ {@code items2} du payload avant l'envoi.
     * <p>Ce champ n'a aucun sens metier : il vient de la signature generique
     * {@code DataModel<Item, String>} cote SIRYF, que le generateur OpenAPI materialise en une liste
     * <em>initialisee a vide</em> sur chaque {@code DataModel...String}. Or SIRYF refuse un corps
     * portant {@code "items2": []} avec « Veuillez fournir les donnees a traiter », alors qu'il
     * accepte le meme corps avec {@code "items2": null} : la liste vide, et elle seule, lui fait
     * conclure qu'il n'a rien recu.
     * <p>Neutralise ici, une fois pour tous les fichiers reglementaires, plutot que sur chaque
     * cablage de feuille : le champ porte le meme nom sur tous les {@code DataModel...} generes.
     */
    private static void neutraliserItems2(Object dataModel) {
        try {
            dataModel.getClass().getMethod("setItems2", List.class).invoke(dataModel, (Object) null);
        } catch (NoSuchMethodException absent) {
            // DataModel sans champ « items2 » : rien a neutraliser
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Neutralisation d'items2 impossible sur " + dataModel.getClass().getName(), e);
        }
    }
}
