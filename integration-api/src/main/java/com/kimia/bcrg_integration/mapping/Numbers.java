package com.kimia.bcrg_integration.mapping;

/**
 * Conversion des montants texte (format francais) en {@link Double}, mutualisee entre les mappers.
 * <p>Gere les separateurs de milliers (espaces normaux, insecables   et fins  ) et la
 * virgule decimale. Chaine vide ou "-" -> {@code null} ; valeur non numerique -> {@link
 * MappingException} (erreur de donnees a remonter ligne/colonne, cf. section 6 du CDC).
 */
final class Numbers {

    private Numbers() {}

    static Double parseFrenchDouble(String raw, String colonne) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        // retire tous les separateurs de milliers (espaces normaux + insecables), puis virgule -> point
        String normalized = s.replaceAll("[\\s\\u00A0\\u202F]+", "").replace(",", ".");
        // Format comptable : un montant negatif s'affiche entre parentheses — « (1 234) » vaut -1234.
        // C'est ce que rend DataFormatter pour un format « #,##0;(#,##0) », courant dans les templates
        // (colonnes « Exposition nette », « Montant net »…). Sans cette lecture, tout negatif recalcule
        // par une formule tombait en erreur de donnee.
        boolean negatifComptable = normalized.startsWith("(") && normalized.endsWith(")");
        if (negatifComptable) normalized = normalized.substring(1, normalized.length() - 1);
        // Cellule au format pourcentage : le tableur affiche « 15% » la ou il stocke 0,15. C'est la
        // valeur stockee qui est le taux (les colonnes de ponderation de PRUD en sont pleines), donc
        // c'est elle qu'on restitue, pas le nombre affiche.
        boolean pourcentage = normalized.endsWith("%");
        if (pourcentage) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            double valeur = Double.parseDouble(normalized);
            if (pourcentage) valeur = valeur / 100d;
            return negatifComptable ? -valeur : valeur;
        } catch (NumberFormatException e) {
            throw new MappingException(
                    "Montant non numerique dans la colonne \"" + colonne + "\" : \"" + raw + "\"");
        }
    }


    /**
     * Montant d'une colonne de <strong>template</strong> : identique a
     * {@link #parseFrenchDouble}, sauf qu'une valeur contenant une lettre est traitee comme une
     * case vide ({@code null}) au lieu d'echouer.
     * <p>Pourquoi : les templates reglementaires annotent leurs colonnes de montant avec la
     * <em>consigne de remplissage</em>, pas avec un montant — renvoi de compte (« N° compte 190 »,
     * « CN°290 » dans SITU_01), renvoi de cellule (« FPN/F46 », « IGEC_18: AT13 », « PRUD_05/W21 »
     * dans PRUD), voire une phrase (« Nombre de dossiers en infraction(PRUD_04) »). Sans cette
     * tolerance, une feuille entiere tombe en {@code ERREUR_DONNEES} sur un template non rempli.
     * <p>Regle volontairement etroite et explicable : <strong>un montant ne contient jamais de
     * lettre</strong> (format FR = chiffres, espaces, virgule, signe). Une valeur numerique mal
     * formee (« 1 O00 » avec un O, « 12..5 ») reste donc refusee, comme partout ailleurs.
     */
    static Double parseMontantOuRenvoi(String raw, String colonne) {
        if (raw != null && raw.chars().anyMatch(Character::isLetter)) {
            return null;                      // consigne du template, pas un montant
        }
        return parseFrenchDouble(raw, colonne);
    }

    /**
     * Entier d'une colonne de <strong>template</strong> : {@link #parseInteger} tolerant aux
     * consignes de remplissage, sur le meme principe que {@link #parseMontantOuRenvoi} (une valeur
     * contenant une lettre est une consigne, pas un nombre). Les colonnes de denombrement des
     * templates IGEC (guichets, clients, effectifs) en portent.
     */
    static Integer parseEntierOuRenvoi(String raw, String colonne) {
        if (raw != null && raw.chars().anyMatch(Character::isLetter)) {
            return null;
        }
        return parseInteger(raw, colonne);
    }

    /**
     * Entier 64 bits d'une colonne de <strong>template</strong> — meme regle que
     * {@link #parseEntierOuRenvoi}, pour les champs {@code Long} de la cible (les effectifs de
     * FINS_14 : {@code cadreSuperieur}, {@code total}).
     */
    static Long parseLongOuRenvoi(String raw, String colonne) {
        Integer valeur = parseEntierOuRenvoi(raw, colonne);
        return (valeur == null) ? null : valeur.longValue();
    }

    /**
     * Numero de ligne d'une <strong>liste</strong>, ou {@code null} si la cellule n'en porte pas.
     * <p>Sert a reconnaitre les lignes qui ne sont pas des enregistrements : les feuilles-listes se
     * terminent par une ligne de total dont la colonne « N° » vaut « TOTAL » et dont les autres
     * colonnes valent « N/A » — non vides, donc invisibles pour un simple test de nom renseigne.
     * <p>Contrairement a {@link #parseInteger}, ne leve pas : c'est une <em>question</em> posee a la
     * ligne (« es-tu un enregistrement ? »), pas une conversion dont l'echec serait une erreur.
     */
    static Integer parseEntierOuNull(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return parseInteger(s, "N°");
        } catch (MappingException pasUnEntier) {
            return null;
        }
    }

    /**
     * Parse un entier au format francais (ex. numero de ligne). Vide/« - » -> {@code null} ; valeur
     * non entiere -> {@link MappingException}. Tolere une ecriture decimale integrale (ex. « 1,0 »).
     */
    static Integer parseInteger(String raw, String colonne) {
        Double d = parseFrenchDouble(raw, colonne);
        if (d == null) return null;
        if (d != Math.rint(d)) {
            throw new MappingException(
                    "Entier attendu dans la colonne \"" + colonne + "\" : \"" + raw + "\"");
        }
        return d.intValue();
    }
}
