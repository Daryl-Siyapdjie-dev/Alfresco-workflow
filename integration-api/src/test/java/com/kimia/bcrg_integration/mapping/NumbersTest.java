package com.kimia.bcrg_integration.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Conversion des montants texte (format français) — {@link Numbers#parseFrenchDouble}.
 * <p>Le cas central ici est le <strong>négatif au format comptable</strong> : le tableur affiche un
 * montant négatif entre parenthèses (« (1 234) » pour −1234) via un format « #,##0;(#,##0) ». Ce cas
 * n'apparaissait pas tant que les formules périmées renvoyaient leur cache à 0 ; il est devenu réel
 * dès que le lecteur a recalculé les formules (colonnes « Exposition nette » de PRUD/APR, « Montant
 * net » du bilan quand l'amortissement dépasse le brut).
 */
class NumbersTest {

    @Test
    void negatif_au_format_comptable_entre_parentheses() {
        // le cas exact tombé en recette (espaces insécables entre les milliers)
        assertEquals(-467765001d, Numbers.parseFrenchDouble("(467 765 001)", "Exposition nette"));
        assertEquals(-1234d, Numbers.parseFrenchDouble("(1 234)", "x"));
        assertEquals(-12.5d, Numbers.parseFrenchDouble("(12,5)", "x"));
    }

    @Test
    void positifs_et_separateurs_inchanges() {
        assertEquals(1234d, Numbers.parseFrenchDouble("1 234", "x"));
        assertEquals(467765001d, Numbers.parseFrenchDouble("467 765 001", "x"));
        assertEquals(12.5d, Numbers.parseFrenchDouble("12,5", "x"));
    }

    @Test
    void pourcentage_reste_le_taux_stocke() {
        assertEquals(0.15d, Numbers.parseFrenchDouble("15%", "taux"));
    }

    @Test
    void vide_tiret_null_donnent_null() {
        assertNull(Numbers.parseFrenchDouble(null, "x"));
        assertNull(Numbers.parseFrenchDouble("", "x"));
        assertNull(Numbers.parseFrenchDouble("  ", "x"));
        assertNull(Numbers.parseFrenchDouble("-", "x"));
    }

    @Test
    void valeur_reellement_non_numerique_reste_refusee() {
        try {
            Numbers.parseFrenchDouble("1 O00", "Montant");   // un O (lettre) au lieu d'un zéro
            throw new AssertionError("aurait dû lever MappingException");
        } catch (MappingException attendu) {
            // message porte la colonne et la valeur fautive
        }
    }
}
