package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.SheetMapper;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Descripteur d'une feuille à intégrer : il relie, pour <em>une</em> feuille, les quatre maillons du
 * pipeline (lecture → mapping → assemblage → envoi). Comme chaque feuille a des types différents
 * (item, DataModel, endpoint), on capture le lien par des fonctions plutôt que par de l'héritage —
 * l'{@link IntegrationService} reste ainsi générique et sans connaissance des types concrets.
 *
 * @param sheetName nom de la feuille dans le classeur (ex. « M.III.HS_BILAN »)
 * @param blocks    tranches de la feuille à lire et mapper, dans l'ordre ; leurs items sont
 *                  concaténés dans un seul payload (voir {@link SheetBlock})
 * @param assembler assemble items + en-tête de transmission → {@code DataModel…String}
 * @param sender    envoie le payload à la BCRG et renvoie la réponse HTTP
 * @param secondTable second tableau de la feuille, d'un autre type, à déposer dans {@code items2} —
 *                  {@code null} pour l'immense majorité des feuilles (voir {@link SecondTable})
 * @param <I>       type d'item CIBLE (ex. {@code SigImfHorsBilan})
 * @param <D>       type du {@code DataModel…String} correspondant
 */
public record SheetJob<I, D>(
        String sheetName,
        List<SheetBlock<I>> blocks,
        BiFunction<List<I>, TransmissionImf, D> assembler,
        Function<D, ResponseEntity<String>> sender,
        SecondTable<D> secondTable) {

    public SheetJob {
        if (blocks == null || blocks.isEmpty())
            throw new IllegalArgumentException("Une feuille comporte au moins un bloc à lire");
        blocks = List.copyOf(blocks);
    }

    /** Feuille sans second tableau — le cas de toutes les feuilles sauf quatre. */
    public SheetJob(String sheetName, List<SheetBlock<I>> blocks,
                    BiFunction<List<I>, TransmissionImf, D> assembler,
                    Function<D, ResponseEntity<String>> sender) {
        this(sheetName, blocks, assembler, sender, null);
    }

    /** Feuille d'un seul bloc, de forme standard (en-tête « CODE », arrêt à la 1re ligne vide). */
    public SheetJob(String sheetName, SheetMapper<I> mapper,
                    BiFunction<List<I>, TransmissionImf, D> assembler,
                    Function<D, ResponseEntity<String>> sender) {
        this(sheetName, SheetLayout.defaults(), mapper, assembler, sender);
    }

    /** Feuille d'un seul bloc, ne changeant que le marqueur d'en-tête (ex. « N°. »). */
    public SheetJob(String sheetName, String headerMarker, SheetMapper<I> mapper,
                    BiFunction<List<I>, TransmissionImf, D> assembler,
                    Function<D, ResponseEntity<String>> sender) {
        this(sheetName, SheetLayout.marker(headerMarker), mapper, assembler, sender);
    }

    /** Feuille d'un seul bloc, de forme donnée. */
    public SheetJob(String sheetName, SheetLayout layout, SheetMapper<I> mapper,
                    BiFunction<List<I>, TransmissionImf, D> assembler,
                    Function<D, ResponseEntity<String>> sender) {
        this(sheetName, List.of(new SheetBlock<>(layout, mapper)), assembler, sender, null);
    }

    /** Feuille d'un seul bloc <strong>suivi d'un second tableau</strong> d'un autre type. */
    public SheetJob(String sheetName, SheetLayout layout, SheetMapper<I> mapper,
                    BiFunction<List<I>, TransmissionImf, D> assembler,
                    Function<D, ResponseEntity<String>> sender,
                    SecondTable<D> secondTable) {
        this(sheetName, List.of(new SheetBlock<>(layout, mapper)), assembler, sender, secondTable);
    }
}
