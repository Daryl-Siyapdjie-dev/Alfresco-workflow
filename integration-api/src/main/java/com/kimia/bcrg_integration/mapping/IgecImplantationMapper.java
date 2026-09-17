package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ImplantationIfi;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_01} (lieux d'implantation) → items {@link ImplantationIfi}
 * (endpoint {@code /api/imf/igec/igec01}).
 * <p>Une agence par ligne : lieu, adresse, ouverture, effectif, guichets, clientèle et encours.
 * Correspondance 1:1 avec le DTO, les 17 champs sont servis.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>⚠️ Cette feuille est la seule dont les intitulés précis sont <strong>au-dessus</strong> de la
 * ligne du marqueur : c'est pourquoi son registre vise la ligne haute (« LIEUX D'IMPLANTATION »)
 * avec un en-tête sur deux lignes, la ligne basse précisant chaque colonne.
 */
@Component
public class IgecImplantationMapper implements SheetMapper<ImplantationIfi> {

    static final String CODE = "N°CODE";
    static final String CAPITALE = "CAPITALE";
    static final String RESTE_DU_PAYS = "RESTE DU PAYS";
    static final String AGENCE = "AGENCE/CAISSE";
    static final String ADRESSE = "ADRESSE";
    static final String OUVERTURE = "Date de 1ère ouverture au public";
    static final String EFFECTIF = "Effectif";
    static final String GUICHETS = "Guichets";
    static final String PS = "*PS";
    static final String IOM = "**IOM";
    static final String CLIENTS_ENTREPRISES = "Clients entreprises";
    static final String CLIENTS_PARTICULIERS = "Clients particuliers";
    static final String RESPONSABLE = "Nom et Prénoms";
    static final String NATIONALITE = "Nationalité";
    static final String DEPOTS = "Dépôts de la clientèle";
    static final String ENCOURS = "Encours crédit";
    static final String CREDITS_DISTRIBUES = "Crédits distribués";

    @Override
    public List<ImplantationIfi> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), CAPITALE, AGENCE, GUICHETS, DEPOTS);
        List<ImplantationIfi> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new ImplantationIfi()
                    .code(code)
                    .capitaleLieuImplantation(cols.get(row, CAPITALE))
                    .resteDuPaysLieuImplantation(cols.get(row, RESTE_DU_PAYS))
                    .agence(cols.get(row, AGENCE))
                    .adresse(cols.get(row, ADRESSE))
                    .datePremiereOuverturePublic(Dates.parseDate(cols.get(row, OUVERTURE), OUVERTURE))
                    .effectif(Numbers.parseEntierOuRenvoi(cols.get(row, EFFECTIF), EFFECTIF))
                    .nbreGuichets(Numbers.parseEntierOuRenvoi(cols.get(row, GUICHETS), GUICHETS))
                    .nbrePS(Numbers.parseEntierOuRenvoi(cols.get(row, PS), PS))
                    .nbreIom(Numbers.parseEntierOuRenvoi(cols.get(row, IOM), IOM))
                    .nbreClientEntreprise(Numbers.parseEntierOuRenvoi(cols.get(row, CLIENTS_ENTREPRISES), CLIENTS_ENTREPRISES))
                    .nbreClientParticulier(Numbers.parseEntierOuRenvoi(cols.get(row, CLIENTS_PARTICULIERS), CLIENTS_PARTICULIERS))
                    .nomPrenomResponsable(cols.get(row, RESPONSABLE))
                    .nationaliteResponsable(cols.get(row, NATIONALITE))
                    .montantDepotClientele(Numbers.parseMontantOuRenvoi(cols.get(row, DEPOTS), DEPOTS))
                    .montantEncoursCredit(Numbers.parseMontantOuRenvoi(cols.get(row, ENCOURS), ENCOURS))
                    .montantCreditDistribue(Numbers.parseMontantOuRenvoi(cols.get(row, CREDITS_DISTRIBUES), CREDITS_DISTRIBUES)));
        }
        return items;
    }
}
