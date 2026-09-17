package com.kimia.bcrg_integration.mapping;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Conversion des dates texte des feuilles ({@code jj/mm/aaaa}) en {@link OffsetDateTime} (minuit UTC),
 * mutualisée entre les mappers. Chaîne vide ou « - » → {@code null} ; date invalide →
 * {@link MappingException} (erreur de données à remonter, cf. §6 du CDC).
 */
final class Dates {

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Dates() {}

    static OffsetDateTime parseDate(String raw, String colonne) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        // Case vide au format comptable : le tableur y affiche un zero (« - 0 », « 0 ») la ou il n'y
        // a rien. La refuser ferait tomber toute la feuille pour une case que personne n'a remplie.
        // La tolerance s'arrete la : seule une valeur qui *est un nombre* passe pour une case vide.
        // Une date mal ecrite (« 2018-05-12 ») n'en est pas un et reste refusee ci-dessous.
        if (estUnNombre(s)) return null;
        try {
            return LocalDate.parse(s, FR_DATE).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new MappingException(
                    "Date invalide (jj/mm/aaaa attendu) dans la colonne \"" + colonne + "\" : \"" + raw + "\"");
        }
    }

    /** Motif d'une date jj/mm/aaaa noyee dans un texte. */
    private static final java.util.regex.Pattern DATE_DANS_TEXTE =
            java.util.regex.Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{4})\\b");

    /**
     * Premiere date jj/mm/aaaa trouvee dans la valeur, ou {@code null} s'il n'y en a pas.
     * <p>Pour les colonnes qui melangent volontairement deux informations — « DATE ET NUMERO DE
     * L'ACTE DE NOMINATION » d'IGEC_04 —, ou seule la date a un champ cible. On extrait donc la
     * date et on ignore le reste, plutot que de refuser la ligne : la cellule est conforme a son
     * intitule, ce n'est pas une erreur de donnees.
     */
    static OffsetDateTime premiereDate(String raw) {
        if (raw == null) return null;
        java.util.regex.Matcher m = DATE_DANS_TEXTE.matcher(raw);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1), FR_DATE).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;                      // 32/13/2020 : ce n'etait pas une date
        }
    }

    /** Vrai si la cellule porte un nombre — donc pas une date, meme mal ecrite. */
    private static boolean estUnNombre(String s) {
        try {
            Double.parseDouble(s.replaceAll("[\s\u00A0\u202F]+", "").replace(",", "."));
            return true;
        } catch (NumberFormatException pasUnNombre) {
            return false;
        }
    }
}
