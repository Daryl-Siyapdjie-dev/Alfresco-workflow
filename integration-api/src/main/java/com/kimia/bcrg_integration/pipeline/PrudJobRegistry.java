package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.AutresNormesPrudentielles;
import com.kimia.bcrg_integration.client.dto.DataModelImfAutresNormesPrudentiellesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCalculFondsPropresNetsCorrigesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCalculFondsPropresNetsString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCalculRatioSolvabiliteImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCoefficientObservationLiquiditeImmediateString;
import com.kimia.bcrg_integration.client.dto.DataModelImfConcoursConsentisImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfConformiteNormesPrudentiellesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCouvertureEmploisStablesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfDivisionRisquesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfImfCollectantDepotString;
import com.kimia.bcrg_integration.client.dto.DataModelImfImfNeCollectantDepotString;
import com.kimia.bcrg_integration.client.dto.DataModelImfRisquesCreditsString;
import com.kimia.bcrg_integration.client.dto.ImfCollectantDepot;
import com.kimia.bcrg_integration.client.dto.ImfNeCollectantDepot;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.PrudAprMapper;
import com.kimia.bcrg_integration.mapping.PrudConcoursMandatairesMapper;
import com.kimia.bcrg_integration.mapping.PrudDivisionRisquesMapper;
import com.kimia.bcrg_integration.mapping.PrudEcnpMapper;
import com.kimia.bcrg_integration.mapping.PrudEmploisStablesMapper;
import com.kimia.bcrg_integration.mapping.PrudFpnMapper;
import com.kimia.bcrg_integration.mapping.PrudFpncMapper;
import com.kimia.bcrg_integration.mapping.PrudLiquiditeImmediateMapper;
import com.kimia.bcrg_integration.mapping.PrudNormeMapper;
import com.kimia.bcrg_integration.mapping.PrudRatioSolvabiliteMapper;
import com.kimia.bcrg_integration.mapping.SheetMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Registre des jobs du fichier <strong>PRUD</strong> (normes prudentielles) : <strong>12 feuilles,
 * 12 endpoints</strong> de la famille {@code /api/imf/prud/}, en correspondance 1:1. Troisième
 * fichier réglementaire câblé, sur le patron éprouvé par {@link SigimfJobRegistry} et
 * {@link SituJobRegistry}.
 *
 * <p><strong>Forme commune aux 12 feuilles</strong>, d'où la {@link #FORME} partagée :
 * <ul>
 *   <li>en-tête repéré par « N°CODE » — écrit tantôt « N°CODE », tantôt « N° CODE » selon la
 *       feuille ; le lecteur compare les intitulés sans tenir compte des espaces ;</li>
 *   <li>en-tête sur <strong>une seule ligne</strong> : là où une deuxième ligne existe, elle ne
 *       numérote que les colonnes (« (1) », « (2) », « (3) = 1 x 2 ») et écraserait les vrais
 *       intitulés ;</li>
 *   <li>lecture jusqu'en bas en ignorant lignes vides et en-têtes répétés : ces feuilles sont
 *       aérées, et PRUD_02 se poursuit dans un second bloc (« Dénominateur : Exigibles ») qui
 *       rouvre son en-tête.</li>
 * </ul>
 * Les lignes de service que cette lecture large ramène (titres de section, numérotation des
 * colonnes, sous-titres « A inclure » / « A déduire ») sont écartées par la règle commune des
 * mappers : <em>une ligne compte si elle porte un code</em>.
 *
 * <p>Contrairement à SITU, la colonne des codes est <strong>remplie dans le template</strong>
 * (« RA0_001 », « FP0_032 », « EPR_0010 », « RC0_001 ») : les items partent donc avec le {@code code}
 * que SIRYF valide, sans dépendre d'un classeur rempli.
 */
@Component
public class PrudJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "PRUD";

    /** Marqueur d'en-tête des feuilles PRUD (les graphies « N° CODE » et « N°CODE » se valent). */
    private static final String MARQUEUR = "N°CODE";

    /** Forme partagée : en-tête sur une ligne, lecture jusqu'en bas (feuilles aérées / en 2 blocs). */
    private static final SheetLayout FORME = SheetLayout.marker(MARQUEUR).skippingBlankRows();

    /** Intitulé de la colonne de libellé de PRUD_06. */
    static final String LIBELLE_AUTRES_NORMES = "AUTRES NORMES PRUDENTIELLES";

    /** Intitulé de la colonne de libellé des deux feuilles IMAO. */
    static final String LIBELLE_RATIOS_HARMONISES = "RATIOS PRUDENTIELS HARMONISES";

    private final Map<String, SheetJob<?, ?>> jobs = new LinkedHashMap<>();

    public PrudJobRegistry(BcrgSender sender) {
        register(sender, "ECNP", new PrudEcnpMapper(),
                DataModelImfConformiteNormesPrudentiellesString::new,
                DataModelImfConformiteNormesPrudentiellesString::items,
                DataModelImfConformiteNormesPrudentiellesString::transmission,
                "/api/imf/prud/ecnp");

        register(sender, "FPNC", new PrudFpncMapper(),
                DataModelImfCalculFondsPropresNetsCorrigesString::new,
                DataModelImfCalculFondsPropresNetsCorrigesString::items,
                DataModelImfCalculFondsPropresNetsCorrigesString::transmission,
                "/api/imf/prud/fpnc");

        register(sender, "FPN", new PrudFpnMapper(),
                DataModelImfCalculFondsPropresNetsString::new,
                DataModelImfCalculFondsPropresNetsString::items,
                DataModelImfCalculFondsPropresNetsString::transmission,
                "/api/imf/prud/fpn");

        register(sender, "APR", new PrudAprMapper(),
                DataModelImfRisquesCreditsString::new,
                DataModelImfRisquesCreditsString::items,
                DataModelImfRisquesCreditsString::transmission,
                "/api/imf/prud/apr");

        register(sender, "PRUD_01", new PrudRatioSolvabiliteMapper(),
                DataModelImfCalculRatioSolvabiliteImfString::new,
                DataModelImfCalculRatioSolvabiliteImfString::items,
                DataModelImfCalculRatioSolvabiliteImfString::transmission,
                "/api/imf/prud/prud_01");

        register(sender, "PRUD_02", new PrudLiquiditeImmediateMapper(),
                DataModelImfCoefficientObservationLiquiditeImmediateString::new,
                DataModelImfCoefficientObservationLiquiditeImmediateString::items,
                DataModelImfCoefficientObservationLiquiditeImmediateString::transmission,
                "/api/imf/prud/prud_02");

        register(sender, "PRUD_03", new PrudEmploisStablesMapper(),
                DataModelImfCouvertureEmploisStablesString::new,
                DataModelImfCouvertureEmploisStablesString::items,
                DataModelImfCouvertureEmploisStablesString::transmission,
                "/api/imf/prud/prud_03");

        register(sender, "PRUD_04", new PrudDivisionRisquesMapper(),
                DataModelImfDivisionRisquesString::new,
                DataModelImfDivisionRisquesString::items,
                DataModelImfDivisionRisquesString::transmission,
                "/api/imf/prud/prud_04");

        register(sender, "PRUD_05", new PrudConcoursMandatairesMapper(),
                DataModelImfConcoursConsentisImfString::new,
                DataModelImfConcoursConsentisImfString::items,
                DataModelImfConcoursConsentisImfString::transmission,
                "/api/imf/prud/prud_05");

        // Trois feuilles de même forme, trois DTO jumeaux : un seul mapper, une fabrique par cible.
        register(sender, "PRUD_06",
                new PrudNormeMapper<>(LIBELLE_AUTRES_NORMES,
                        (code, libelle, montant, seuil, norme) -> new AutresNormesPrudentielles()
                                .code(code).libelle(libelle).montant(montant)
                                .seuilObserve(seuil).norme(norme)),
                DataModelImfAutresNormesPrudentiellesString::new,
                DataModelImfAutresNormesPrudentiellesString::items,
                DataModelImfAutresNormesPrudentiellesString::transmission,
                "/api/imf/prud/prud_06");

        register(sender, "IMAO_ICD",
                new PrudNormeMapper<>(LIBELLE_RATIOS_HARMONISES,
                        (code, libelle, montant, seuil, norme) -> new ImfCollectantDepot()
                                .code(code).libelle(libelle).montant(montant)
                                .seuilObserve(seuil).norme(norme)),
                DataModelImfImfCollectantDepotString::new,
                DataModelImfImfCollectantDepotString::items,
                DataModelImfImfCollectantDepotString::transmission,
                "/api/imf/prud/imao_icd");

        register(sender, "IMAO_INCD",
                new PrudNormeMapper<>(LIBELLE_RATIOS_HARMONISES,
                        (code, libelle, montant, seuil, norme) -> new ImfNeCollectantDepot()
                                .code(code).libelle(libelle).montant(montant)
                                .seuilObserve(seuil).norme(norme)),
                DataModelImfImfNeCollectantDepotString::new,
                DataModelImfImfNeCollectantDepotString::items,
                DataModelImfImfNeCollectantDepotString::transmission,
                "/api/imf/prud/imao_incd");
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

    /** Le job d'intégration d'une feuille PRUD, ou {@code null} si la feuille n'est pas prise en charge. */
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
