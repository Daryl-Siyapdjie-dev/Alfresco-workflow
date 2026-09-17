package com.kimia.bcrg_integration.excel;

/**
 * Forme d'une feuille du point de vue du lecteur. Les feuilles réglementaires ne sont pas toutes de
 * simples tableaux « un en-tête, des lignes contiguës » : certaines portent leur en-tête sur plusieurs
 * lignes (cellules fusionnées), d'autres aèrent leurs données avec des lignes vides ou répètent leur
 * ligne d'en-tête à chaque sous-bloc, d'autres enfin <strong>empilent deux tableaux différents</strong>
 * dans la même feuille. Ces variations sont décrites ici plutôt qu'en dur dans le lecteur.
 *
 * @param headerMarker   1re cellule de la ligne d'en-tête (« CODE » par défaut, « N°. » pour les listes)
 * @param headerRowSpan  nombre de lignes que couvre l'en-tête ; au-delà de 1, le nom d'une colonne est
 *                       la <strong>dernière</strong> valeur non vide de cette colonne sur l'intervalle
 *                       (ex. M.XII.RCS : « RECOUVREMENT… » en ligne 6 précisé par « 1er trimestre » en ligne 7)
 * @param skipBlankRows  {@code true} : les lignes vides <em>internes</em> et les lignes d'en-tête répétées
 *                       sont ignorées et la lecture va jusqu'au bas de la feuille (feuilles aérées ou en
 *                       plusieurs blocs de même forme, ex. M.XI.RPCS, M.XXI.BALANCES AGEES) ;
 *                       {@code false} : la lecture s'arrête à la 1re ligne vide (comportement historique,
 *                       qui protège des blocs annexes situés plus bas dans la feuille)
 * @param occurrence     rang de la ligne d'en-tête à retenir quand le marqueur apparaît plusieurs fois
 *                       (1 = la première, défaut). Sert aux feuilles qui empilent deux tableaux
 *                       <em>de types différents</em> sous le même marqueur : le second tableau vise
 *                       l'occurrence 2 (ex. FINS_12, FINS_14, FINS_16)
 * @param endMarker      intitulé qui <strong>termine</strong> la lecture quand il réapparaît en tête de
 *                       ligne, ou {@code null}. Nécessaire dès qu'une feuille empile deux tableaux : sans
 *                       lui, {@code skipBlankRows} lit jusqu'en bas et le premier tableau avalerait les
 *                       lignes du second (les charges arriveraient parmi les produits). À ne pas confondre
 *                       avec l'en-tête <em>répété</em> d'un même tableau, que {@code skipBlankRows} ignore
 *                       pour poursuivre (SITU_01, PRUD_02)
 */
public record SheetLayout(String headerMarker, int headerRowSpan, boolean skipBlankRows,
                          int occurrence, String endMarker) {

    public SheetLayout {
        if (headerMarker == null || headerMarker.isBlank())
            throw new IllegalArgumentException("Marqueur d'en-tête obligatoire");
        if (headerRowSpan < 1)
            throw new IllegalArgumentException("headerRowSpan doit valoir au moins 1");
        if (occurrence < 1)
            throw new IllegalArgumentException("occurrence doit valoir au moins 1");
    }

    /** Feuille standard : en-tête « CODE » sur une ligne, arrêt à la 1re ligne vide. */
    public static SheetLayout defaults() {
        return marker(ExcelReader.DEFAULT_HEADER_MARKER);
    }

    /** Feuille standard avec un autre marqueur d'en-tête (ex. « N°. »). */
    public static SheetLayout marker(String headerMarker) {
        return new SheetLayout(headerMarker, 1, false, 1, null);
    }

    /** Même forme, mais l'en-tête couvre {@code rows} lignes. */
    public SheetLayout spanning(int rows) {
        return new SheetLayout(headerMarker, rows, skipBlankRows, occurrence, endMarker);
    }

    /** Même forme, mais les lignes vides internes et en-têtes répétés sont ignorés. */
    public SheetLayout skippingBlankRows() {
        return new SheetLayout(headerMarker, headerRowSpan, true, occurrence, endMarker);
    }

    /** Même forme, mais l'en-tête visé est la {@code n}-ième apparition du marqueur dans la feuille. */
    public SheetLayout occurrence(int n) {
        return new SheetLayout(headerMarker, headerRowSpan, skipBlankRows, n, endMarker);
    }

    /** Même forme, mais la lecture s'arrête quand {@code marqueur} réapparaît en tête de ligne. */
    public SheetLayout stoppingAt(String marqueur) {
        return new SheetLayout(headerMarker, headerRowSpan, skipBlankRows, occurrence, marqueur);
    }
}
