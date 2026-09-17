package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.client.BcrgSender;
import com.kimia.bcrg_integration.client.dto.DataModelImfBalanceImfString;
import com.kimia.bcrg_integration.excel.SheetLayout;
import com.kimia.bcrg_integration.mapping.BalgBalanceMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Registre du fichier <strong>BALG</strong> (balance générale) : une seule feuille, un seul endpoint
 * — le plus court des six registres, et le dernier du périmètre.
 *
 * <p><strong>Le nom de l'onglet porte le millésime</strong> (« BALANCE H.IMF2026 ») : il changera
 * d'un exercice à l'autre, alors que la feuille, elle, ne changera pas. Un registre qui exigerait le
 * nom exact cesserait donc de reconnaître la balance au 1er janvier — silencieusement, puisque
 * « TOUS » ignore les feuilles qu'il ne connaît pas. D'où la reconnaissance par
 * <strong>préfixe</strong> : tout onglet dont l'intitulé commence par « BALANCE » est la balance, et
 * le job construit porte le nom <em>réel</em> de l'onglet, celui sous lequel le lecteur le trouvera.
 *
 * <p>C'est le seul registre dont l'endpoint ne vit pas sous {@code /api/imf/} : la balance des IMF a
 * sa propre racine, {@code /api/balanceimf} — à ne pas confondre avec {@code /api/balance}, réservée
 * aux banques et dont le schéma, plus riche, attend un numéro de client et un secteur d'activité que
 * le template IMF ne porte pas.
 */
@Component
public class BalgJobRegistry implements JobRegistry {

    /** Code du fichier réglementaire couvert par ce registre. */
    public static final String CODE_FICHIER = "BALG";

    /** Nom de l'onglet dans le template en vigueur — celui que « TOUS » vise par défaut. */
    public static final String FEUILLE = "BALANCE H.IMF2026";

    /** Ce qui identifie l'onglet quel que soit son millésime (cf. javadoc de classe). */
    private static final String PREFIXE = "BALANCE";

    /** Endpoint de la balance des IMF (et non {@code /api/balance}, qui est celui des banques). */
    private static final String ENDPOINT = "/api/balanceimf";

    /**
     * En-tête sur <strong>deux lignes</strong> : titres de groupe fusionnés (« SOLDE INITIAL »…) puis
     * « DEBIT »/« CREDIT » sous chacun. Arrêt à la première ligne vide, qui sépare le tableau des
     * comptes du bloc de consignes de remplissage situé en bas de feuille.
     */
    private static final SheetLayout FORME = SheetLayout.marker("N° Compte").spanning(2);

    private final BcrgSender sender;
    private final BalgBalanceMapper mapper = new BalgBalanceMapper();

    public BalgJobRegistry(BcrgSender sender) {
        this.sender = sender;
    }

    @Override
    public String codeFichier() {
        return CODE_FICHIER;
    }

    /**
     * Le job de la balance si {@code sheetName} désigne l'onglet de balance — quel que soit son
     * millésime — ou {@code null} sinon. Le job porte le nom reçu, pas le nom canonique : c'est sous
     * ce nom que la feuille sera relue dans le classeur.
     */
    @Override
    public SheetJob<?, ?> forSheet(String sheetName) {
        if (!estLaBalance(sheetName)) {
            return null;
        }
        return new SheetJob<>(sheetName, FORME, mapper,
                Payloads.assembler(DataModelImfBalanceImfString::new,
                        DataModelImfBalanceImfString::items,
                        DataModelImfBalanceImfString::transmission),
                payload -> sender.post(ENDPOINT, payload));
    }

    @Override
    public Set<String> sheets() {
        return Set.of(FEUILLE);
    }

    private static boolean estLaBalance(String sheetName) {
        return sheetName != null
                && sheetName.trim().toUpperCase(Locale.ROOT).startsWith(PREFIXE);
    }
}
