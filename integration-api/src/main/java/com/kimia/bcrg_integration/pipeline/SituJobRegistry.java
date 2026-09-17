package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationBilanActifString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationBilanPassifImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationHorsBilanImfString;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.SheetMapper;
import com.kimia.bcrg_integration.mapping.SituActifMapper;
import com.kimia.bcrg_integration.mapping.SituHorsBilanMapper;
import com.kimia.bcrg_integration.mapping.SituPassifMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Registre des jobs du fichier <strong>SITU</strong> (situation comptable) : trois feuilles, trois
 * endpoints de la famille {@code /api/imf/situ/}. Même patron que {@link SigimfJobRegistry} — un
 * mapper par feuille, un {@code DataModel…String} par endpoint — appliqué au deuxième fichier
 * réglementaire.
 *
 * <p><strong>Forme commune aux trois feuilles</strong>, d'où la {@link #FORME} partagée :
 * <ul>
 *   <li>l'en-tête commence par « N°CODE » (et non « CODE ») ;</li>
 *   <li>il tient sur <strong>deux lignes</strong> : la 2e précise les colonnes de montants
 *       (« RESIDENTS » / « NON-RESIDENTS » sous « ACTIF BRUT » ou « GNF ») ;</li>
 *   <li>la lecture va jusqu'en bas en ignorant lignes vides et en-têtes répétés, car SITU_01 et
 *       SITU_02 se poursuivent dans un <strong>second bloc</strong> (« ACTIF (2) », « PASSIF (2) »)
 *       de mêmes colonnes ; leurs lignes retombent donc sur les intitulés du premier bloc et
 *       partent dans le même envoi.</li>
 * </ul>
 * Les lignes parasites que cette lecture large ramène (titres du 2e bloc, ligne de numérotation
 * des colonnes, « TOTAL ACTIF ») sont écartées par les mappers : <em>une ligne compte si elle
 * porte un n° de compte</em>.
 *
 * <p>⚠️ La colonne « N°CODE » est <strong>vide dans le template</strong> : les items partent donc
 * sans {@code code}. SIRYF valide les codes de ligne des feuilles SIGIMF (« Code X est
 * introuvable ») — à vérifier pour SITU sur un classeur rempli avant de conclure.
 */
@Component
public class SituJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "SITU";

    /** Marqueur d'en-tête des feuilles SITU (« N°CODE », avec l'espace insécable du template). */
    private static final String MARQUEUR = "N°CODE";

    /** Forme partagée : en-tête sur 2 lignes, lecture jusqu'en bas (feuilles en deux blocs). */
    private static final SheetLayout FORME =
            SheetLayout.marker(MARQUEUR).spanning(2).skippingBlankRows();

    private final Map<String, SheetJob<?, ?>> jobs = new LinkedHashMap<>();

    public SituJobRegistry(BcrgSender sender) {
        register(sender, "SITU_01", new SituActifMapper(),
                DataModelImfSituationBilanActifString::new,
                DataModelImfSituationBilanActifString::items,
                DataModelImfSituationBilanActifString::transmission, "/api/imf/situ/situ01");

        register(sender, "SITU_02", new SituPassifMapper(),
                DataModelImfSituationBilanPassifImfString::new,
                DataModelImfSituationBilanPassifImfString::items,
                DataModelImfSituationBilanPassifImfString::transmission, "/api/imf/situ/situ02");

        register(sender, "SITU_03", new SituHorsBilanMapper(),
                DataModelImfSituationHorsBilanImfString::new,
                DataModelImfSituationHorsBilanImfString::items,
                DataModelImfSituationHorsBilanImfString::transmission, "/api/imf/situ/situ03");
    }

    private <I, D> void register(BcrgSender sender, String sheet, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        jobs.put(sheet, new SheetJob<>(sheet, FORME, mapper,
                Payloads.assembler(dataModelFactory, setItems, setTransmission),
                payload -> sender.post(endpoint, payload)));
    }

    @Override
    public String codeFichier() {
        return CODE_FICHIER;
    }

    /** Le job d'intégration d'une feuille SITU, ou {@code null} si la feuille n'est pas prise en charge. */
    @Override
    public SheetJob<?, ?> forSheet(String sheetName) {
        return jobs.get(sheetName);
    }

    /** Noms des feuilles prises en charge (ordre du fichier). */
    @Override
    public Set<String> sheets() {
        return jobs.keySet();
    }
}
