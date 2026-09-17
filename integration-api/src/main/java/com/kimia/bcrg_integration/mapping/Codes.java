package com.kimia.bcrg_integration.mapping;

/**
 * Reconnaissance des <strong>codes de ligne</strong> des templates réglementaires, mutualisée entre
 * les mappers qui décident « cette ligne part / cette ligne reste à quai ».
 */
final class Codes {

    private Codes() {}

    /**
     * Vrai si la valeur est un code de ligne. Un code est un <strong>jeton compact</strong> :
     * {@code RA0_001}, {@code FP0_032}, {@code EPR_1510}, {@code TT0_0270}, {@code APR_04} — jamais
     * d'espace.
     * <p>Ce détail suffit à écarter ce qui traîne dans la colonne des codes sans en être un : la note
     * de bas de feuille de PRUD_06 (« *EP: Etat Périodique »), qui partirait sinon comme un item et
     * ferait répondre à SIRYF « Code … est introuvable ».
     */
    /**
     * Variante qui ecarte en plus le <strong>nom de la feuille</strong>.
     * <p>Les feuilles qui empilent deux tableaux rappellent leur nom en tete du bandeau qui les
     * separe — « FINS_09 » ecrit seul en colonne A. Sans espace, ce nom a toutes les apparences d'un
     * code ; transmis comme tel, il se fait refuser par SIRYF (« Code ... est introuvable »), car il
     * ne designe aucune ligne du referentiel.
     */
    static boolean estCodeDeLigne(String valeur, String nomFeuille) {
        return estCodeDeLigne(valeur)
                && !valeur.trim().equalsIgnoreCase(nomFeuille == null ? "" : nomFeuille.trim());
    }

    static boolean estCodeDeLigne(String valeur) {
        return valeur != null && !valeur.isBlank()
                && valeur.chars().noneMatch(Character::isWhitespace);
    }
}
