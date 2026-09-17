package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.*;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Registre des {@link SheetJob} <strong>réels</strong> pour le fichier SIGIMF : pour chaque feuille
 * couverte, câble le mapper, l'assemblage du {@code DataModel…String} et l'endpoint d'envoi.
 * <p>Les mappers sont des composants purs et sans état → instanciés ici pour garder le registre
 * autonome (un seul point de vérité feuille → endpoint). L'envoi passe par {@link BcrgSender}, ce qui
 * rend le registre testable hors-ligne.
 */
@Component
public class SigimfJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "SIGIMF";

    private static final String CODE = ExcelReader.DEFAULT_HEADER_MARKER;   // "CODE"
    private static final String NUM = "N°.";                                // feuilles-listes
    /** Feuille aérée de lignes vides / en plusieurs blocs de même forme (lecture jusqu'en bas). */
    private static final SheetLayout AEREE = SheetLayout.defaults().skippingBlankRows();
    /** Feuille dont l'en-tête tient sur deux lignes (cellules fusionnées). */
    private static final SheetLayout ENTETE_2_LIGNES = SheetLayout.defaults().spanning(2);
    /** Feuilles d'indicateurs : en-tête « N°. », agrégats hors tableau après une ligne vide. */
    private static final SheetLayout INDICATEURS = SheetLayout.marker(SigImfRapportIndicateurMapper.HEADER_MARKER);

    private final Map<String, SheetJob<?, ?>> jobs = new LinkedHashMap<>();

    public SigimfJobRegistry(BcrgSender sender) {
        register(sender, "M.0.INFO.G", CODE, new SigImfInfoGeneraleMapper(),
                DataModelImfSigImfInformationGeneraleString::new,
                DataModelImfSigImfInformationGeneraleString::items,
                DataModelImfSigImfInformationGeneraleString::transmission, "/api/sigimf/m0infog");

        // M.I.BILAN : actif puis passif, sous deux en-têtes « CODE » successifs — le second se
        // reconnaît à sa colonne « PASSIF ». Un seul envoi, le côté étant porté par le booléen actif.
        register(sender, "M.I.BILAN", List.of(
                        new SheetBlock<>(SheetLayout.marker(CODE),
                                new SigImfBilanMapper(SigImfBilanMapper.COL_ACTIF, true)),
                        new SheetBlock<>(SheetLayout.marker(CODE).occurrence(2),
                                new SigImfBilanMapper(SigImfBilanMapper.COL_PASSIF, false))),
                DataModelImfSigImfBilanString::new,
                DataModelImfSigImfBilanString::items,
                DataModelImfSigImfBilanString::transmission, "/api/sigimf/m1bilan");

        // M.II.RESULTAT : charges puis produits, même forme que le bilan — le booléen product
        // distingue les deux blocs.
        register(sender, "M.II.RESULTAT", List.of(
                        new SheetBlock<>(SheetLayout.marker(CODE),
                                new SigImfCompteResultatMapper(SigImfCompteResultatMapper.COL_CHARGES, false)),
                        new SheetBlock<>(SheetLayout.marker(CODE).occurrence(2),
                                new SigImfCompteResultatMapper(SigImfCompteResultatMapper.COL_PRODUITS, true))),
                DataModelImfSigImfCompteResultatString::new,
                DataModelImfSigImfCompteResultatString::items,
                DataModelImfSigImfCompteResultatString::transmission, "/api/sigimf/m2resultat");

        register(sender, "M.III.HS_BILAN", CODE, new SigImfHorsBilanMapper(),
                DataModelImfSigImfHorsBilanString::new,
                DataModelImfSigImfHorsBilanString::items,
                DataModelImfSigImfHorsBilanString::transmission, "/api/sigimf/m3horsbilan");

        // M.IV.IMPAYE : tableau des impayés, puis ratios de portefeuille à risque (PAR 1/30/90),
        // dont les colonnes diffèrent — d'où un second mapper plutôt qu'un paramètre.
        register(sender, "M.IV.IMPAYE", List.of(
                        new SheetBlock<>(SheetLayout.marker(CODE), new SigImfImpayeMapper()),
                        new SheetBlock<>(SheetLayout.marker(CODE).occurrence(2),
                                new SigImfImpayeRatiosMapper())),
                DataModelImfSigImfSituationImpayeCreditString::new,
                DataModelImfSigImfSituationImpayeCreditString::items,
                DataModelImfSigImfSituationImpayeCreditString::transmission, "/api/sigimf/m4impaye");

        register(sender, "M.V.PFC", CODE, new SigImfPfcMapper(),
                DataModelImfSigImfDonnePortefeuilleCreditString::new,
                DataModelImfSigImfDonnePortefeuilleCreditString::items,
                DataModelImfSigImfDonnePortefeuilleCreditString::transmission, "/api/sigimf/m5pfc");

        register(sender, "M.VI.ADM.DIR", NUM, new SigImfCreditDirigeantMapper(),
                DataModelImfSigImfSCreditADirigeantString::new,
                DataModelImfSigImfSCreditADirigeantString::items,
                DataModelImfSigImfSCreditADirigeantString::transmission, "/api/sigimf/m6admdir");

        register(sender, "M.VII.PERSONNEL", NUM, new SigImfCreditPersonnelMapper(),
                DataModelImfSigImfSCreditAPersonnelString::new,
                DataModelImfSigImfSCreditAPersonnelString::items,
                DataModelImfSigImfSCreditAPersonnelString::transmission, "/api/sigimf/m7personnel");

        register(sender, "M.VIII.DIX.DEB", NUM, new SigImfDebiteurImportantMapper(),
                DataModelImfSigImfDebiteurImportantString::new,
                DataModelImfSigImfDebiteurImportantString::items,
                DataModelImfSigImfDebiteurImportantString::transmission, "/api/sigimf/m8dixdeb");

        register(sender, "M.X.RSL", CODE, new SigImfMxrslMapper(),
                DataModelImfSigImfMxrslString::new,
                DataModelImfSigImfMxrslString::items,
                DataModelImfSigImfMxrslString::transmission, "/api/sigimf/m10rsl");

        register(sender, "M.XI.RPCS", AEREE, new SigImfRepartitionPCreditMapper(),
                DataModelImfSigImfRepartitionPCreditString::new,
                DataModelImfSigImfRepartitionPCreditString::items,
                DataModelImfSigImfRepartitionPCreditString::transmission, "/api/sigimf/m11rpcs");

        register(sender, "M.XII.RCS", ENTETE_2_LIGNES, new SigImfRCreanceSouffranceMapper(),
                DataModelImfSigImfRCreanceSouffranceString::new,
                DataModelImfSigImfRCreanceSouffranceString::items,
                DataModelImfSigImfRCreanceSouffranceString::transmission, "/api/sigimf/m12rcs");

        // OATA / FS / CERS partagent le même DTO et le même mapper, endpoints différents
        SigImfRapportCollecteMapper rapportCollecte = new SigImfRapportCollecteMapper();
        register(sender, "M.XIII.OATA", CODE, rapportCollecte,
                DataModelImfSigImfRapportCollecteString::new,
                DataModelImfSigImfRapportCollecteString::items,
                DataModelImfSigImfRapportCollecteString::transmission, "/api/sigimf/m13oata");
        register(sender, "M.XIV.FS", CODE, rapportCollecte,
                DataModelImfSigImfRapportCollecteString::new,
                DataModelImfSigImfRapportCollecteString::items,
                DataModelImfSigImfRapportCollecteString::transmission, "/api/sigimf/m14fs");
        register(sender, "M.XVI.CERS", CODE, rapportCollecte,
                DataModelImfSigImfRapportCollecteString::new,
                DataModelImfSigImfRapportCollecteString::items,
                DataModelImfSigImfRapportCollecteString::transmission, "/api/sigimf/m16cers");

        // M.XV.RECAP : récapitulatif + tableau « Couverture Géographique » (colonnes différentes,
        // même DTO) → deux blocs, un seul envoi
        register(sender, "M.XV.RECAP", List.of(
                        new SheetBlock<>(SheetLayout.defaults(), new SigImfRecapMapper()),
                        new SheetBlock<>(SheetLayout.marker(SigImfRecapGeoMapper.HEADER_MARKER),
                                new SigImfRecapGeoMapper())),
                DataModelImfSigImfRecapString::new,
                DataModelImfSigImfRecapString::items,
                DataModelImfSigImfRecapString::transmission, "/api/sigimf/m15recap");

        // M.IX.SG : un formulaire de dix postes, pas un tableau — ses colonnes de détail changent de
        // sens tous les trois rangs, et l'intitulé d'un poste est souvent porté par le bloc de titres
        // qui le précède. Tout cela est dans le mapper ; la forme, elle, reste standard et aérée.
        register(sender, "M.IX.SG", SheetLayout.defaults().spanning(2).skippingBlankRows(),
                new SigImfActiviteStatGeneraleMapper(),
                DataModelImfSigImfActiviteStatGeneraleString::new,
                DataModelImfSigImfActiviteStatGeneraleString::items,
                DataModelImfSigImfActiviteStatGeneraleString::transmission, "/api/sigimf/m9sg");

        register(sender, "M.XVII.PROV", AEREE, new SigImfTauxProvisionMapper(),
                DataModelImfSigImfTauxProvisionString::new,
                DataModelImfSigImfTauxProvisionString::items,
                DataModelImfSigImfTauxProvisionString::transmission, "/api/sigimf/m17prov");

        register(sender, "M.XVIII.DIX.CREANCIERS", NUM, new SigImfCreancierImportantMapper(),
                DataModelImfSigImfCreancierPlusImportantString::new,
                DataModelImfSigImfCreancierPlusImportantString::items,
                DataModelImfSigImfCreancierPlusImportantString::transmission, "/api/sigimf/m18dixcreanciers");

        register(sender, "M.XIX.CREDITS.RESTRUCTURES", NUM, new SigImfCreditRestructureMapper(),
                DataModelImfSigImfCreditRestructureString::new,
                DataModelImfSigImfCreditRestructureString::items,
                DataModelImfSigImfCreditRestructureString::transmission, "/api/sigimf/m19creditsrestructures");

        register(sender, "M.XX.DIX.BENEF.SIGN", NUM, new SigImfBenefSignMapper(),
                DataModelImfSigImfBenefSignString::new,
                DataModelImfSigImfBenefSignString::items,
                DataModelImfSigImfBenefSignString::transmission, "/api/sigimf/m20benefsign");

        register(sender, "M.XXI.BALANCES AGEES", AEREE, new SigImfBalancesAgeeMapper(),
                DataModelImfSigImfBalancesAgeeString::new,
                DataModelImfSigImfBalancesAgeeString::items,
                DataModelImfSigImfBalancesAgeeString::transmission, "/api/sigimf/m21balancesagees");

        // Les 4 feuilles d'indicateurs partagent le même DTO et le même mapper, endpoints différents
        SigImfRapportIndicateurMapper indicateurs = new SigImfRapportIndicateurMapper();
        register(sender, "M.XXII.INDIC.PRUDENTIELS", INDICATEURS, indicateurs,
                DataModelImfSigImfRapportIndicateurString::new,
                DataModelImfSigImfRapportIndicateurString::items,
                DataModelImfSigImfRapportIndicateurString::transmission, "/api/sigimf/m22indicprudentiels");
        register(sender, "M.XXII.RATIO.PRUDENTIELS", INDICATEURS, indicateurs,
                DataModelImfSigImfRapportIndicateurString::new,
                DataModelImfSigImfRapportIndicateurString::items,
                DataModelImfSigImfRapportIndicateurString::transmission, "/api/sigimf/m22ratioprudentiels");
        register(sender, "TAB_AUTRES_INDIC_1", INDICATEURS, indicateurs,
                DataModelImfSigImfRapportIndicateurString::new,
                DataModelImfSigImfRapportIndicateurString::items,
                DataModelImfSigImfRapportIndicateurString::transmission, "/api/sigimf/tabautresindic1");
        register(sender, "TAB_AUTRES_INDIC_2", INDICATEURS, indicateurs,
                DataModelImfSigImfRapportIndicateurString::new,
                DataModelImfSigImfRapportIndicateurString::items,
                DataModelImfSigImfRapportIndicateurString::transmission, "/api/sigimf/tabautresindic2");
    }

    /** Variante « feuille standard » : seul le marqueur d'en-tête change. */
    private <I, D> void register(BcrgSender sender, String sheet, String marker, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        register(sender, sheet, SheetLayout.marker(marker), mapper, dataModelFactory, setItems,
                setTransmission, endpoint);
    }

    private <I, D> void register(BcrgSender sender, String sheet, SheetLayout layout, SheetMapper<I> mapper,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        jobs.put(sheet, new SheetJob<>(sheet, layout, mapper,
                assembler(dataModelFactory, setItems, setTransmission),
                payload -> sender.post(endpoint, payload)));
    }

    /** Variante « feuille en plusieurs blocs » : les items de chaque bloc partent dans le même envoi. */
    private <I, D> void register(BcrgSender sender, String sheet, List<SheetBlock<I>> blocks,
                                 Supplier<D> dataModelFactory,
                                 BiConsumer<D, List<I>> setItems,
                                 BiConsumer<D, TransmissionImf> setTransmission,
                                 String endpoint) {
        jobs.put(sheet, new SheetJob<>(sheet, blocks, assembler(dataModelFactory, setItems, setTransmission),
                payload -> sender.post(endpoint, payload)));
    }

    private <I, D> BiFunction<List<I>, TransmissionImf, D> assembler(Supplier<D> dataModelFactory,
                                                                     BiConsumer<D, List<I>> setItems,
                                                                     BiConsumer<D, TransmissionImf> setTransmission) {
        return Payloads.assembler(dataModelFactory, setItems, setTransmission);
    }

    @Override
    public String codeFichier() {
        return CODE_FICHIER;
    }

    /** Le job d'intégration d'une feuille SIGIMF, ou {@code null} si la feuille n'est pas prise en charge. */
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
            Map.entry("M.I.BILAN", Periodicite.TRIMESTRIELLE),
            Map.entry("M.II.RESULTAT", Periodicite.TRIMESTRIELLE),
            Map.entry("M.III.HS_BILAN", Periodicite.TRIMESTRIELLE),
            Map.entry("M.IV.IMPAYE", Periodicite.TRIMESTRIELLE),
            Map.entry("M.IX.SG", Periodicite.TRIMESTRIELLE),
            Map.entry("M.XIX.CREDITS.RESTRUCTURES", Periodicite.TRIMESTRIELLE));

    @Override
    public Periodicite periodicite(String sheetName) {
        return RYTHMES.getOrDefault(sheetName, Periodicite.MENSUELLE);
    }

    /** Noms des feuilles prises en charge (ordre d'insertion). */
    @Override
    public Set<String> sheets() {
        return jobs.keySet();
    }
}
