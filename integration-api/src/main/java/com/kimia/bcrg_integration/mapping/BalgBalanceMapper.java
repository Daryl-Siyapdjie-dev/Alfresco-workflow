package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.BalanceImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la <strong>balance générale</strong> (fichier BALG, feuille « BALANCE H.IMF… ») → items
 * {@link BalanceImf} (endpoint {@code /api/balanceimf}, DataModel
 * {@code DataModelImfBalanceImfString}).
 *
 * <p><strong>Une feuille, un tableau, neuf colonnes — et neuf champs cible.</strong> C'est la
 * correspondance la plus directe de tout le périmètre : le plan comptable de l'IMF, une ligne par
 * compte, avec pour chacun trois paires débit/crédit (solde initial, mouvements de la période,
 * solde final). Les colonnes suivantes du template — « Code Poste débit », « Code Poste crédit »,
 * « [Resident] », « Agent économique », « Secteur d'Activité » — <strong>n'ont pas d'équivalent
 * dans {@code BalanceImf}</strong> : elles appartiennent au schéma {@code Balance} des banques, plus
 * riche. Elles sont donc lues et ignorées, sans perte pour l'IMF.
 *
 * <p><strong>Pourquoi des noms de colonnes suffixés.</strong> L'en-tête tient sur deux lignes : la
 * première porte les titres de groupe fusionnés (« SOLDE INITIAL », « MVT PERIODE », « SOLDE
 * FINAL »), la seconde répète « DEBIT » et « CREDIT » sous chacun. Trois colonnes s'appellent donc
 * « DEBIT » et trois « CREDIT ». Le lecteur rend ces homonymes distincts en les numérotant, dans
 * l'ordre de la feuille — d'où {@code DEBIT}, {@code DEBIT (2)}, {@code DEBIT (3)}, qui sont
 * respectivement l'initial, le mouvement et le final.
 *
 * <p><strong>Ligne retenue = ligne portant un numéro de compte.</strong> Cette seule règle écarte
 * les trois lignes de totaux qui closent le tableau (« TOTAUX COMPTES DE BILAN », « TOTAUX COMPTES
 * DE GESTION », « TOTAL ») : SIRYF recalcule ses agrégats, transmettre les totaux du classeur
 * reviendrait à compter deux fois. Un numéro de compte est fait de chiffres et de rien d'autre.
 *
 * <p><strong>Montants lus strictement</strong>, sans la tolérance {@code parseMontantOuRenvoi} des
 * autres templates : une balance ne porte pas de consigne de remplissage dans ses colonnes de
 * montant, et un chiffre mal lu y serait une erreur comptable silencieuse. Mieux vaut une feuille
 * refusée qu'une balance fausse.
 */
@Component
public class BalgBalanceMapper implements SheetMapper<BalanceImf> {

    static final String COMPTE = "N° Compte";
    static final String INTITULE = "Intitulé compte";
    static final String DEVISE = "Devise";

    /** Sous « SOLDE INITIAL » — 1re paire débit/crédit de la 2e ligne d'en-tête. */
    static final String INITIAL_DEBIT = "DEBIT";
    static final String INITIAL_CREDIT = "CREDIT";
    /** Sous « MVT PERIODE » — 2e paire, que le lecteur suffixe pour la distinguer de la 1re. */
    static final String MOUVEMENT_DEBIT = "DEBIT (2)";
    static final String MOUVEMENT_CREDIT = "CREDIT (2)";
    /** Sous « SOLDE FINAL » — 3e paire. */
    static final String FINAL_DEBIT = "DEBIT (3)";
    static final String FINAL_CREDIT = "CREDIT (3)";

    @Override
    public List<BalanceImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), COMPTE, INTITULE, DEVISE,
                INITIAL_DEBIT, INITIAL_CREDIT, MOUVEMENT_DEBIT, MOUVEMENT_CREDIT,
                FINAL_DEBIT, FINAL_CREDIT);

        List<BalanceImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String compte = cols.get(row, COMPTE);
            if (!estNumeroDeCompte(compte)) continue;         // ligne de totaux, ou ligne de commentaire
            String intitule = cols.get(row, INTITULE);
            items.add(new BalanceImf()
                    .chapitre(compte)
                    .intituleChapitre(intitule.isEmpty() ? null : intitule)
                    .codeDevise(cols.get(row, DEVISE))
                    .initialDebit(Numbers.parseFrenchDouble(cols.get(row, INITIAL_DEBIT), INITIAL_DEBIT))
                    .initialCredit(Numbers.parseFrenchDouble(cols.get(row, INITIAL_CREDIT), INITIAL_CREDIT))
                    .mouvementDebit(Numbers.parseFrenchDouble(cols.get(row, MOUVEMENT_DEBIT), MOUVEMENT_DEBIT))
                    .mouvementCredit(Numbers.parseFrenchDouble(cols.get(row, MOUVEMENT_CREDIT), MOUVEMENT_CREDIT))
                    .soldeFinalDebit(Numbers.parseFrenchDouble(cols.get(row, FINAL_DEBIT), FINAL_DEBIT))
                    .soldeFinalCredit(Numbers.parseFrenchDouble(cols.get(row, FINAL_CREDIT), FINAL_CREDIT)));
        }
        return items;
    }

    /**
     * Vrai si la cellule porte un <strong>numéro de compte</strong> — que des chiffres. Les lignes
     * de totaux du bas de tableau y écrivent du texte, les lignes de commentaire n'y écrivent rien :
     * aucune des deux ne décrit un compte de la balance.
     */
    private static boolean estNumeroDeCompte(String valeur) {
        String v = valeur.trim();
        return !v.isEmpty() && v.chars().allMatch(Character::isDigit);
    }
}
