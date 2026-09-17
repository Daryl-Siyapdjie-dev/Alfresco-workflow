package com.kimia.bcrg_integration.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Résout les colonnes attendues dans les en-têtes d'une feuille, de façon tolérante à la casse et
 * aux espaces. Mutualisé entre les mappers pour lire une valeur par nom de colonne logique plutôt
 * que par position (robuste aux réordonnancements de colonnes dans les fichiers).
 */
final class Columns {

    private final Map<String, String> normalizedToActual;

    private Columns(Map<String, String> normalizedToActual) {
        this.normalizedToActual = normalizedToActual;
    }

    /**
     * Construit le résolveur et vérifie la présence des colonnes {@code required}.
     * <p>Les colonnes <strong>sans intitulé</strong> restent adressables par leur position, sous le
     * nom que le lecteur leur donne dans la ligne : {@code col0}, {@code col6}… Quelques colonnes de
     * template n'ont pas d'autre identité — soit qu'elles n'aient jamais reçu d'en-tête (bloc
     * géographique de {@code M.XV.RECAP}), soit que leur intitulé, fusionné, commence une colonne
     * plus à gauche que les valeurs (FINS_01, FINS_02).
     */
    static Columns of(List<String> headers, String... required) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h == null || h.isEmpty()) {
                String position = "col" + i;              // même convention que le lecteur
                map.put(normalize(position), position);
            } else {
                map.put(normalize(h), h);
            }
        }
        for (String r : required) {
            if (!map.containsKey(normalize(r))) {
                throw new MappingException(
                        "Colonne obligatoire absente : « " + r + " » (en-têtes présents : " + headers + ")");
            }
        }
        return new Columns(map);
    }

    /** Valeur de la colonne {@code col} pour la ligne donnée ; chaîne vide si absente/nulle. */
    String get(Map<String, String> row, String col) {
        String actual = normalizedToActual.get(normalize(col));
        String value = (actual == null) ? null : row.get(actual);
        return (value == null) ? "" : value;
    }

    /**
     * Casse et <strong>tous les espaces</strong> ignorés : les en-têtes des feuilles matricielles
     * alignent leur libellé à coups d'espaces (« Agriculture Elevage       Pêche »), parfois
     * insécables, parfois avec un retour à la ligne interne au milieu du libellé ; et le
     * même intitulé s'écrit « N°CODE » sur une feuille de PRUD, « N° CODE » sur la suivante.
     * Aucune de ces variations ne désigne une autre colonne.
     */
    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase().replaceAll("[\\s\\u00A0\\u202F]+", "");
    }
}
