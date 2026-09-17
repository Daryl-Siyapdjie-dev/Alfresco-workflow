package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfImplantationIfiString;
import com.kimia.bcrg_integration.client.dto.DataModelImfExpositionsPrincipalesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfDeposantsPrincipauxString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompositionDirectionImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompositionConseilAdminString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompositionCapitalImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfInformationCommissaireCompteImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfDixCreanciersString;
import com.kimia.bcrg_integration.client.dto.DataModelImfDixBeneficiaresString;
import com.kimia.bcrg_integration.client.dto.DataModelImfRepartitionEncoursCreditsString;
import com.kimia.bcrg_integration.client.dto.DataModelImfCreditsRestructuresString;
import com.kimia.bcrg_integration.client.dto.DataModelImfRepartitionCreditsDeboursesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfIndicateursActivitesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfSituationComptesActifsInactifsString;
import com.kimia.bcrg_integration.client.dto.DataModelImfEngagementsSalairesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfEngagementsActionnairesAdminMandatairesString;
import com.kimia.bcrg_integration.client.dto.DataModelImfInfoComplementairesCalculsFPNString;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.IgecImplantationMapper;
import com.kimia.bcrg_integration.mapping.IgecExpositionsMapper;
import com.kimia.bcrg_integration.mapping.IgecDeposantsMapper;
import com.kimia.bcrg_integration.mapping.IgecDirectionMapper;
import com.kimia.bcrg_integration.mapping.IgecConseilAdminMapper;
import com.kimia.bcrg_integration.mapping.IgecCapitalMapper;
import com.kimia.bcrg_integration.mapping.IgecCommissairesMapper;
import com.kimia.bcrg_integration.mapping.IgecCreanciersMapper;
import com.kimia.bcrg_integration.mapping.IgecBeneficiairesMapper;
import com.kimia.bcrg_integration.mapping.IgecEncoursCreditsMapper;
import com.kimia.bcrg_integration.mapping.IgecCreditsRestructuresMapper;
import com.kimia.bcrg_integration.mapping.IgecCreditsDeboursesMapper;
import com.kimia.bcrg_integration.mapping.IgecIndicateursMapper;
import com.kimia.bcrg_integration.mapping.IgecComptesActifsMapper;
import com.kimia.bcrg_integration.mapping.IgecEngagementsSalariesMapper;
import com.kimia.bcrg_integration.mapping.IgecEngagementsActionnairesMapper;
import com.kimia.bcrg_integration.mapping.IgecInfoFondsPropresMapper;
import com.kimia.bcrg_integration.mapping.SheetMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Registre des jobs du fichier <strong>IGEC</strong> (informations générales et complémentaires) :
 * <strong>17 feuilles, 17 endpoints</strong> de la famille {@code /api/imf/igec/}, en correspondance
 * 1:1. Quatrième fichier réglementaire câblé, sur le patron de {@link SigimfJobRegistry},
 * {@link SituJobRegistry} et {@link PrudJobRegistry}.
 *
 * <p><strong>Trois formes de feuille</strong> suffisent, et la différence tient à un seul point : où
 * se trouve l'intitulé <em>précis</em> d'une colonne par rapport à la ligne du marqueur.
 * <ul>
 *   <li>{@link #FORME} — {@code IGEC_02} à {@code IGEC_16} : marqueur « N°CODE », en-tête sur
 *       <strong>deux lignes</strong>, la ligne basse précisant le chapeau fusionné de la ligne haute
 *       (« NOMBRE » → « Guichets », « *PS », « **IOM ») ;</li>
 *   <li>{@link #IMPLANTATION} — {@code IGEC_01} : ses intitulés précis sont <strong>au-dessus</strong>
 *       du marqueur (« ADRESSE », « Effectif », « Date de 1ère ouverture »). On vise donc la ligne
 *       haute par son propre premier intitulé — plutôt que d'apprendre au lecteur à remonter ;</li>
 *   <li>{@link #FONDS_PROPRES} — {@code IGEC_17} : marqueur « CODE » et en-tête sur
 *       <strong>une</strong> ligne, la suivante ne faisant que numéroter les colonnes.</li>
 * </ul>
 * Les trois lisent jusqu'en bas en ignorant les lignes vides : ces feuilles sont aérées, et la règle
 * commune des mappers — <em>une ligne compte si elle porte un code</em> — écarte ce que cette
 * lecture large ramène (numérotation des colonnes, restes d'en-tête).
 *
 * <p>Comme PRUD, et contrairement à SITU, la colonne des codes est <strong>remplie dans le
 * template</strong> ({@code RN0_0100}, {@code TT0_0230}, {@code EPR_0140}).
 */
@Component
public class IgecJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "IGEC";

    /** Forme commune : « N°CODE », en-tête sur 2 lignes, lecture jusqu'en bas. */
    private static final SheetLayout FORME =
            SheetLayout.marker("N°CODE").spanning(2).skippingBlankRows();

    /** IGEC_01 : en-tête visé une ligne plus haut, ses intitulés précis y étant portés. */
    private static final SheetLayout IMPLANTATION =
            SheetLayout.marker("LIEUX D'IMPLANTATION").spanning(2).skippingBlankRows();

    /** IGEC_17 : marqueur « CODE », en-tête sur une seule ligne. */
    private static final SheetLayout FONDS_PROPRES =
            SheetLayout.marker("CODE").skippingBlankRows();

    private final Map<String, SheetJob<?, ?>> jobs = new LinkedHashMap<>();

    public IgecJobRegistry(BcrgSender sender) {
        register(sender, "IGEC_01", IMPLANTATION, new IgecImplantationMapper(),
                DataModelImfImplantationIfiString::new, DataModelImfImplantationIfiString::items,
                DataModelImfImplantationIfiString::transmission, "/api/imf/igec/igec01");

        register(sender, "IGEC_02", FORME, new IgecExpositionsMapper(),
                DataModelImfExpositionsPrincipalesString::new, DataModelImfExpositionsPrincipalesString::items,
                DataModelImfExpositionsPrincipalesString::transmission, "/api/imf/igec/igec02");

        register(sender, "IGEC_03", FORME, new IgecDeposantsMapper(),
                DataModelImfDeposantsPrincipauxString::new, DataModelImfDeposantsPrincipauxString::items,
                DataModelImfDeposantsPrincipauxString::transmission, "/api/imf/igec/igec03");

        register(sender, "IGEC_04", FORME, new IgecDirectionMapper(),
                DataModelImfCompositionDirectionImfString::new, DataModelImfCompositionDirectionImfString::items,
                DataModelImfCompositionDirectionImfString::transmission, "/api/imf/igec/igec04");

        register(sender, "IGEC_05", FORME, new IgecConseilAdminMapper(),
                DataModelImfCompositionConseilAdminString::new, DataModelImfCompositionConseilAdminString::items,
                DataModelImfCompositionConseilAdminString::transmission, "/api/imf/igec/igec05");

        register(sender, "IGEC_06", FORME, new IgecCapitalMapper(),
                DataModelImfCompositionCapitalImfString::new, DataModelImfCompositionCapitalImfString::items,
                DataModelImfCompositionCapitalImfString::transmission, "/api/imf/igec/igec06");

        register(sender, "IGEC_07", FORME, new IgecCommissairesMapper(),
                DataModelImfInformationCommissaireCompteImfString::new, DataModelImfInformationCommissaireCompteImfString::items,
                DataModelImfInformationCommissaireCompteImfString::transmission, "/api/imf/igec/igec07");

        register(sender, "IGEC_08", FORME, new IgecCreanciersMapper(),
                DataModelImfDixCreanciersString::new, DataModelImfDixCreanciersString::items,
                DataModelImfDixCreanciersString::transmission, "/api/imf/igec/igec08");

        register(sender, "IGEC_09", FORME, new IgecBeneficiairesMapper(),
                DataModelImfDixBeneficiaresString::new, DataModelImfDixBeneficiaresString::items,
                DataModelImfDixBeneficiaresString::transmission, "/api/imf/igec/igec09");

        register(sender, "IGEC_10", FORME, new IgecEncoursCreditsMapper(),
                DataModelImfRepartitionEncoursCreditsString::new, DataModelImfRepartitionEncoursCreditsString::items,
                DataModelImfRepartitionEncoursCreditsString::transmission, "/api/imf/igec/igec10");

        register(sender, "IGEC_11", FORME, new IgecCreditsRestructuresMapper(),
                DataModelImfCreditsRestructuresString::new, DataModelImfCreditsRestructuresString::items,
                DataModelImfCreditsRestructuresString::transmission, "/api/imf/igec/igec11");

        register(sender, "IGEC_12", FORME, new IgecCreditsDeboursesMapper(),
                DataModelImfRepartitionCreditsDeboursesString::new, DataModelImfRepartitionCreditsDeboursesString::items,
                DataModelImfRepartitionCreditsDeboursesString::transmission, "/api/imf/igec/igec12");

        register(sender, "IGEC_13", FORME, new IgecIndicateursMapper(),
                DataModelImfIndicateursActivitesString::new, DataModelImfIndicateursActivitesString::items,
                DataModelImfIndicateursActivitesString::transmission, "/api/imf/igec/igec13");

        register(sender, "IGEC_14", FORME, new IgecComptesActifsMapper(),
                DataModelImfSituationComptesActifsInactifsString::new, DataModelImfSituationComptesActifsInactifsString::items,
                DataModelImfSituationComptesActifsInactifsString::transmission, "/api/imf/igec/igec14");

        register(sender, "IGEC_15", FORME, new IgecEngagementsSalariesMapper(),
                DataModelImfEngagementsSalairesString::new, DataModelImfEngagementsSalairesString::items,
                DataModelImfEngagementsSalairesString::transmission, "/api/imf/igec/igec15");

        register(sender, "IGEC_16", FORME, new IgecEngagementsActionnairesMapper(),
                DataModelImfEngagementsActionnairesAdminMandatairesString::new, DataModelImfEngagementsActionnairesAdminMandatairesString::items,
                DataModelImfEngagementsActionnairesAdminMandatairesString::transmission, "/api/imf/igec/igec16");

        register(sender, "IGEC_17", FONDS_PROPRES, new IgecInfoFondsPropresMapper(),
                DataModelImfInfoComplementairesCalculsFPNString::new, DataModelImfInfoComplementairesCalculsFPNString::items,
                DataModelImfInfoComplementairesCalculsFPNString::transmission, "/api/imf/igec/igec17");
    }

    private <I, D> void register(BcrgSender sender, String sheet, SheetLayout forme, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        jobs.put(sheet, new SheetJob<>(sheet, forme, mapper,
                Payloads.assembler(dataModelFactory, setItems, setTransmission),
                payload -> sender.post(endpoint, payload)));
    }

    @Override
    public String codeFichier() {
        return CODE_FICHIER;
    }

    /** Le job d'intégration d'une feuille IGEC, ou {@code null} si la feuille n'est pas prise en charge. */
    @Override
    public SheetJob<?, ?> forSheet(String sheetName) {
        return jobs.get(sheetName);
    }

    /**
     * Rythme de remise des feuilles qui ne sont pas mensuelles. Une feuille absente de cette table
     * est traitée comme mensuelle : le module l'envoie, et laisse SIRYF trancher.
     * <p>Ces rythmes viennent des refus de SIRYF lui-même (« La date fournie n'est pas la fin du
     * … »), et non d'une documentation — {@code BILAN.md} en tient le relevé.
     */
    private static final Map<String, Periodicite> RYTHMES = Map.ofEntries(
            Map.entry("IGEC_01", Periodicite.SEMESTRIELLE),
            Map.entry("IGEC_11", Periodicite.SEMESTRIELLE),
            Map.entry("IGEC_12", Periodicite.SEMESTRIELLE),
            Map.entry("IGEC_13", Periodicite.SEMESTRIELLE),
            Map.entry("IGEC_14", Periodicite.SEMESTRIELLE),
            Map.entry("IGEC_04", Periodicite.ANNUELLE),
            Map.entry("IGEC_05", Periodicite.ANNUELLE),
            Map.entry("IGEC_06", Periodicite.ANNUELLE),
            Map.entry("IGEC_07", Periodicite.ANNUELLE),
            Map.entry("IGEC_17", Periodicite.ANNUELLE));

    @Override
    public Periodicite periodicite(String sheetName) {
        return RYTHMES.getOrDefault(sheetName, Periodicite.MENSUELLE);
    }

    /** Noms des feuilles prises en charge (ordre du fichier). */
    @Override
    public Set<String> sheets() {
        return jobs.keySet();
    }
}
