package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfBalanceAgeePortefeuilleString;
import com.kimia.bcrg_integration.client.dto.DataModelImfChargePersonnelImfNombreEmployeImf;
import com.kimia.bcrg_integration.client.dto.DataModelImfCompteResultatProduitCompteResultatCharges;
import com.kimia.bcrg_integration.client.dto.DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance;
import com.kimia.bcrg_integration.client.dto.DataModelImfResiduelleRessourceImfDureeResiduelleRessourceImf;
import com.kimia.bcrg_integration.client.dto.DataModelImfBilanHorsBilanImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfEtablissementCreditImfVentilationTypeDetenteurs;
import com.kimia.bcrg_integration.client.dto.DataModelImfImmobiisationAmmortissementImfString;
import com.kimia.bcrg_integration.client.dto.DataModelImfRessourceCollecteImfString;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.FinsAncienneteSouffranceMapper;
import com.kimia.bcrg_integration.mapping.FinsBalanceAgeeMapper;
import com.kimia.bcrg_integration.mapping.FinsChargesPersonnelMapper;
import com.kimia.bcrg_integration.mapping.FinsChargesResultatMapper;
import com.kimia.bcrg_integration.mapping.FinsCompteResultatMapper;
import com.kimia.bcrg_integration.mapping.FinsDureeResiduelleMapper;
import com.kimia.bcrg_integration.mapping.FinsNombreEmployesMapper;
import com.kimia.bcrg_integration.mapping.FinsNombreRessourcesMapper;
import com.kimia.bcrg_integration.mapping.FinsProvisionsSouffranceMapper;
import com.kimia.bcrg_integration.mapping.FinsConcoursEconomieMapper;
import com.kimia.bcrg_integration.mapping.FinsImmobilisationsMapper;
import com.kimia.bcrg_integration.mapping.FinsRessourcesCollecteesMapper;
import com.kimia.bcrg_integration.mapping.FinsTitresMapper;
import com.kimia.bcrg_integration.mapping.FinsVentilationSectorielleMapper;
import com.kimia.bcrg_integration.mapping.SheetMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Registre des jobs du fichier <strong>FINS</strong> (états financiers), famille
 * {@code /api/imf/fins/}. Cinquième fichier réglementaire câblé, sur le patron des précédents.
 *
 * <p><strong>Périmètre : 12 feuilles sur 17</strong> — toutes celles qui ont un endpoint IMF.
 * {@code FINS_05}, {@code 06}, {@code 10}, {@code 11} et {@code 13} n'en ont pas : le Swagger n'expose
 * que douze routes sous {@code /api/imf/fins/}, et le {@code /api/fins/fins11…15} qui existe est la
 * famille <em>banques</em>, celle qui nous répond 409. <strong>Question ouverte côté BCRG.</strong>
 *
 * <p><strong>Quatre feuilles portent deux tableaux</strong> de natures différentes, qui partent dans
 * le même envoi mais dans deux listes distinctes ({@link SecondTable}) : {@code FINS_09} (produits
 * puis charges), {@code FINS_12} (encours en souffrance puis provisions requises), {@code FINS_14}
 * (charges de personnel puis nombre d'employés), {@code FINS_16} (capital restant dû puis nombre).
 * Leur {@code DataModel} l'annonce : son {@code items2} n'y est pas l'artefact {@code String}
 * habituel mais un type à part entière. Chacune déclare donc <strong>où s'arrête son premier
 * tableau</strong> ({@code stoppingAt}) et <strong>quelle occurrence du marqueur ouvre le
 * second</strong> ({@code occurrence}) — sans quoi le premier avalerait les lignes du second, et les
 * charges partiraient parmi les produits.
 *
 * <p><strong>Cinq formes de feuille</strong>, la différence tenant au nombre de lignes de chapeaux
 * au-dessus des intitulés : {@code FINS_01} en a trois (« RESIDENTS » → « Ménages » →
 * « Particuliers »), {@code FINS_02} quatre, {@code FINS_03}/{@code 04}/{@code 15} deux, les autres
 * une seule — celles dont la deuxième ligne ne fait que numéroter les colonnes, et l'inclure
 * remplacerait « Cadres Supérieurs » par « 1 ».
 */
@Component
public class FinsJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "FINS";

    /** Une seule ligne d'en-tête ; la suivante numérote les colonnes et n'en fait pas partie. */
    private static final SheetLayout SIMPLE =
            SheetLayout.marker("N°CODE").skippingBlankRows();

    /** Deux lignes : un chapeau précisé en dessous (« BILAN » → « GNF » / « % »). */
    private static final SheetLayout DEUX_LIGNES =
            SheetLayout.marker("N°CODE").spanning(2).skippingBlankRows();

    /** Trois lignes : « RESIDENTS » → « Ménages » → « Particuliers » (FINS_01). */
    private static final SheetLayout TROIS_LIGNES =
            SheetLayout.marker("N°CODE").spanning(3).skippingBlankRows();

    /** Quatre lignes : FINS_02 empile un niveau de chapeau de plus. */
    private static final SheetLayout QUATRE_LIGNES =
            SheetLayout.marker("N°CODE").spanning(4).skippingBlankRows();

    /** FINS_08_GNF est la seule feuille du fichier dont le marqueur est « CODE ». */
    private static final SheetLayout SIMPLE_CODE =
            SheetLayout.marker("CODE").skippingBlankRows();

    private final Map<String, SheetJob<?, ?>> jobs = new LinkedHashMap<>();

    public FinsJobRegistry(BcrgSender sender) {
        register(sender, "FINS_01", TROIS_LIGNES, new FinsRessourcesCollecteesMapper(),
                DataModelImfRessourceCollecteImfString::new,
                DataModelImfRessourceCollecteImfString::items,
                DataModelImfRessourceCollecteImfString::transmission, "/api/imf/fins/fins01");

        register(sender, "FINS_02", QUATRE_LIGNES, new FinsConcoursEconomieMapper(),
                DataModelImfRessourceCollecteImfString::new,
                DataModelImfRessourceCollecteImfString::items,
                DataModelImfRessourceCollecteImfString::transmission, "/api/imf/fins/fins02");

        register(sender, "FINS_03", DEUX_LIGNES, new FinsVentilationSectorielleMapper(),
                DataModelImfBilanHorsBilanImfString::new,
                DataModelImfBilanHorsBilanImfString::items,
                DataModelImfBilanHorsBilanImfString::transmission, "/api/imf/fins/fins03");

        register(sender, "FINS_04", DEUX_LIGNES, new FinsVentilationSectorielleMapper(),
                DataModelImfBilanHorsBilanImfString::new,
                DataModelImfBilanHorsBilanImfString::items,
                DataModelImfBilanHorsBilanImfString::transmission, "/api/imf/fins/fins04");

        register(sender, "FINS_07_GNF", SIMPLE, new FinsTitresMapper("N°CODE", "LIBELLES"),
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::new,
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::items,
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::transmission,
                "/api/imf/fins/fins07GNF");

        register(sender, "FINS_08_GNF", SIMPLE_CODE, new FinsTitresMapper("CODE", "DETTES"),
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::new,
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::items,
                DataModelImfEtablissementCreditImfVentilationTypeDetenteurs::transmission,
                "/api/imf/fins/fins08GNF");

        register(sender, "FINS_15", DEUX_LIGNES, new FinsImmobilisationsMapper(),
                DataModelImfImmobiisationAmmortissementImfString::new,
                DataModelImfImmobiisationAmmortissementImfString::items,
                DataModelImfImmobiisationAmmortissementImfString::transmission, "/api/imf/fins/fins15");

        register(sender, "FINS_17", SIMPLE, new FinsBalanceAgeeMapper(),
                DataModelImfBalanceAgeePortefeuilleString::new,
                DataModelImfBalanceAgeePortefeuilleString::items,
                DataModelImfBalanceAgeePortefeuilleString::transmission, "/api/imf/fins/fins17");

        // ---- Les quatre feuilles à deux tableaux -------------------------------------------------
        // Le premier tableau s'arrête là où commence le second ; le second vise la 2e occurrence du
        // marqueur — ou son propre marqueur, « CODE », pour FINS_09.

        register(sender, "FINS_09", SIMPLE.spanning(2).stoppingAt("CODE"), new FinsCompteResultatMapper(),
                DataModelImfCompteResultatProduitCompteResultatCharges::new,
                DataModelImfCompteResultatProduitCompteResultatCharges::items,
                DataModelImfCompteResultatProduitCompteResultatCharges::transmission,
                "/api/imf/fins/fins09",
                SecondTable.of(SIMPLE_CODE.spanning(2), new FinsChargesResultatMapper(),
                        DataModelImfCompteResultatProduitCompteResultatCharges::items2));

        register(sender, "FINS_12", SIMPLE.stoppingAt("N°CODE"), new FinsAncienneteSouffranceMapper(),
                DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance::new,
                DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance::items,
                DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance::transmission,
                "/api/imf/fins/fins12",
                SecondTable.of(SIMPLE.occurrence(2), new FinsProvisionsSouffranceMapper(),
                        DataModelImfEncourSouffranceBrutImfProvisionCreditsSouffrance::items2));

        register(sender, "FINS_14", SIMPLE.stoppingAt("N°CODE"), new FinsChargesPersonnelMapper(),
                DataModelImfChargePersonnelImfNombreEmployeImf::new,
                DataModelImfChargePersonnelImfNombreEmployeImf::items,
                DataModelImfChargePersonnelImfNombreEmployeImf::transmission,
                "/api/imf/fins/fins14",
                SecondTable.of(SIMPLE.occurrence(2).spanning(2), new FinsNombreEmployesMapper(),
                        DataModelImfChargePersonnelImfNombreEmployeImf::items2));

        register(sender, "FINS_16", SIMPLE.stoppingAt("N°CODE"), new FinsDureeResiduelleMapper(),
                DataModelImfResiduelleRessourceImfDureeResiduelleRessourceImf::new,
                DataModelImfResiduelleRessourceImfDureeResiduelleRessourceImf::items,
                DataModelImfResiduelleRessourceImfDureeResiduelleRessourceImf::transmission,
                "/api/imf/fins/fins16",
                SecondTable.of(SIMPLE.occurrence(2), new FinsNombreRessourcesMapper(),
                        DataModelImfResiduelleRessourceImfDureeResiduelleRessourceImf::items2));
    }

    private <I, D> void register(BcrgSender sender, String sheet, SheetLayout forme, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        register(sender, sheet, forme, mapper, dataModelFactory, setItems, setTransmission, endpoint, null);
    }

    private <I, D> void register(BcrgSender sender, String sheet, SheetLayout forme, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint,
                                 SecondTable<D> secondTable) {
        jobs.put(sheet, new SheetJob<>(sheet, forme, mapper,
                Payloads.assembler(dataModelFactory, setItems, setTransmission),
                payload -> sender.post(endpoint, payload), secondTable));
    }

    @Override
    public String codeFichier() {
        return CODE_FICHIER;
    }

    /** Le job d'intégration d'une feuille FINS, ou {@code null} si la feuille n'est pas prise en charge. */
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
            Map.entry("FINS_07_GNF", Periodicite.SEMESTRIELLE),
            Map.entry("FINS_08_GNF", Periodicite.SEMESTRIELLE),
            Map.entry("FINS_14", Periodicite.ANNUELLE),
            Map.entry("FINS_15", Periodicite.ANNUELLE));

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
