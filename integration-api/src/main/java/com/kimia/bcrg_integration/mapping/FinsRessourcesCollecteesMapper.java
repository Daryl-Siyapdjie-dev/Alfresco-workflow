package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.RessourceCollecteImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code FINS_01} — ventilation par agent économique des ressources
 * collectées auprès de la clientèle → items {@link RessourceCollecteImf}
 * (endpoint {@code /api/imf/fins/fins01}).
 * <p>En-tête sur <strong>trois lignes</strong> : « RESIDENTS » chapeaute des groupes
 * (« Sociétés non financières », « Ménages ») eux-mêmes précisés en troisième ligne
 * (« SNF publiques », « Particuliers »). La ligne la plus basse l'emporte, c'est elle qui nomme.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune à tous les fichiers :
 * elle écarte les chapeaux d'en-tête que la lecture large ramène et la ligne qui numérote les
 * colonnes (« 1 | 2 | 5 = (1+3) »).
 * <p><strong>Colonne lue par position.</strong> Dans cette feuille, l'intitulé « LIBELLES » est
 * une cellule fusionnée qui commence une colonne <em>avant</em> celle où les valeurs sont
 * saisies : le nom résout la colonne col5, vide sur les lignes de données. On lit donc la
 * colonne col6, que le lecteur nomme {@code col6} faute d'intitulé propre. Un test sur le
 * template réel verrouille ce décalage — précédent : le bloc géographique de {@code M.XV.RECAP}.
 * <p><strong>Écart assumé</strong> : le DTO détaille l'État en trois postes ({@code adminCentrale},
 * {@code adminLocaleRegionale}, {@code administrationSecuriteSociale}) là où le template n'a qu'une
 * colonne « Etat et organismes assimilés ». Celle-ci alimente {@code etatOrganismeAssimile} ; les
 * trois autres restent vides — les répartir serait inventer une ventilation.
 * <p>{@code valeurTotalLigne} n'a pas non plus de colonne source (comme sur SITU).
 */
@Component
public class FinsRessourcesCollecteesMapper implements SheetMapper<RessourceCollecteImf> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° Compte";
    static final String LIBELLE = "col6";        // « LIBELLES » est en col5, les valeurs en col6
    static final String ETAT = "Etat et organismes assimilés";
    static final String SNF_PUBLIQUES = "SNF publiques";
    static final String AUTRES_SNF = "Autres SNF";
    static final String ENTREPRISES = "Entreprises individuelles";
    static final String PARTICULIERS = "Particuliers";
    static final String ISBL = "ISBL";
    static final String ASSURANCES = "Assurances et caisses de retraite";
    static final String AUTRES_INTERMEDIAIRES = "Autres intermédiaires financiers";
    static final String NON_RESIDENTS = "NON RESIDENTS";
    static final String TOTAL = "TOTAL";

    @Override
    public List<RessourceCollecteImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUM_COMPTE, LIBELLE, ETAT, TOTAL);
        List<RessourceCollecteImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new RessourceCollecteImf()
                    .code(code)
                    .numCpte(cols.get(row, NUM_COMPTE))
                    .libelle(cols.get(row, LIBELLE))
                    .etatOrganismeAssimile(Numbers.parseMontantOuRenvoi(cols.get(row, ETAT), ETAT))
                    .snfPublique(Numbers.parseMontantOuRenvoi(cols.get(row, SNF_PUBLIQUES), SNF_PUBLIQUES))
                    .autreSnf(Numbers.parseMontantOuRenvoi(cols.get(row, AUTRES_SNF), AUTRES_SNF))
                    .entrepriseIndividuelle(Numbers.parseMontantOuRenvoi(cols.get(row, ENTREPRISES), ENTREPRISES))
                    .particulier(Numbers.parseMontantOuRenvoi(cols.get(row, PARTICULIERS), PARTICULIERS))
                    .institutionSansButLucratif(Numbers.parseMontantOuRenvoi(cols.get(row, ISBL), ISBL))
                    .assurancesCaissesRetraite(Numbers.parseMontantOuRenvoi(cols.get(row, ASSURANCES), ASSURANCES))
                    .autresIntermediaresFinanciers(Numbers.parseMontantOuRenvoi(cols.get(row, AUTRES_INTERMEDIAIRES), AUTRES_INTERMEDIAIRES))
                    .nonResident(Numbers.parseMontantOuRenvoi(cols.get(row, NON_RESIDENTS), NON_RESIDENTS))
                    .total(Numbers.parseMontantOuRenvoi(cols.get(row, TOTAL), TOTAL)));
        }
        return items;
    }
}
