package com.kimia.bcrg_integration.excel;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Lecteur générique des feuilles Excel au format SIRYF.
 * <p>But : transformer une feuille en données exploitables (métadonnées + lignes
 * colonne→valeur), sans interpréter le métier (le mapping vers les DTO = Lot 3).
 */
@Component

public class ExcelReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);

    private final DataFormatter formatter = new DataFormatter(Locale.FRANCE);

    /** Liste des noms de feuilles du classeur. */
    public List<String> sheetNames(Workbook wb) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));
        return names;
    }

    /** Marqueur d'en-tête par défaut (1re cellule de la ligne d'en-tête sur la plupart des feuilles). */
    public static final String DEFAULT_HEADER_MARKER = "CODE";

    /** Lit une feuille (marqueur d'en-tête « CODE »). */
    public SheetTable readSheet(Workbook wb, String sheetName) {
        return readSheet(wb, sheetName, DEFAULT_HEADER_MARKER);
    }

    /**
     * Lit une feuille avec un <strong>marqueur d'en-tête paramétrable</strong> : la ligne d'en-tête
     * est repérée par la valeur de sa 1re cellule (ex. « CODE » pour la plupart des feuilles,
     * « N°. » pour les listes M.VI.ADM.DIR / M.VII.PERSONNEL).
     */
    public SheetTable readSheet(Workbook wb, String sheetName, String headerMarker) {
        return readSheet(wb, sheetName, SheetLayout.marker(headerMarker));
    }

    /**
     * Lit une feuille selon la {@link SheetLayout} décrite : marqueur d'en-tête, en-tête éventuellement
     * sur plusieurs lignes, et tolérance aux lignes vides internes / en-têtes répétés.
     */
    public SheetTable readSheet(Workbook wb, String sheetName, SheetLayout layout) {
        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) throw new IllegalArgumentException("Feuille introuvable : " + sheetName);

        int headerRow = findHeaderRow(sheet, layout.headerMarker(), layout.occurrence());
        if (headerRow < 0)
            throw new IllegalStateException("En-tête '" + layout.headerMarker() + "'"
                    + (layout.occurrence() > 1 ? " (occurrence " + layout.occurrence() + ")" : "")
                    + " introuvable dans " + sheetName);

        SheetMetadata meta = readMetadata(sheet, headerRow);
        List<String> headers = readHeaders(sheet, headerRow, layout.headerRowSpan());
        int lastHeaderRow = headerRow + layout.headerRowSpan() - 1;
        List<Map<String, String>> rows = readDataRows(sheet, lastHeaderRow, headers, layout);
        return new SheetTable(sheetName, meta, headers, rows);
    }

    /**
     * En-tête = première ligne dont la <strong>1re cellule non vide</strong> vaut {@code marker}.
     * <p>Comparaison insensible à la casse et aux espaces (bord et suites internes) : les intitulés du
     * template sont parfois doublés d'espaces. On accepte une 1re cellule vide car certains blocs
     * commencent leur en-tête en 2e colonne (bloc « Couverture Géographique » de M.XV.RECAP), et on
     * balaie toute la feuille car un bloc annexe peut se trouver loin du haut.
     */
    private int findHeaderRow(Sheet sheet, String marker, int occurrence) {
        String target = normalizeLabel(marker);
        int vues = 0;
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String first = firstNonEmpty(row);
            if (first != null && normalizeLabel(first).equals(target) && ++vues == occurrence) return r;
        }
        return -1;
    }

    /**
     * Normalise un <strong>marqueur d'en-t\u00EAte</strong> : casse et <em>tous</em> les espaces ignor\u00E9s.
     * <p>Les espaces sont supprim\u00E9s, pas seulement r\u00E9duits : le m\u00EAme marqueur s'\u00E9crit \u00AB N\u00B0CODE \u00BB sur
     * sept feuilles de PRUD et \u00AB N\u00B0 CODE \u00BB sur les cinq autres \u2014 deux graphies du m\u00EAme intitul\u00E9. Cela
     * vaut aussi pour la d\u00E9tection d'un en-t\u00EAte r\u00E9p\u00E9t\u00E9 en cours de feuille (PRUD_02, dont le second
     * bloc se rouvre par \u00AB N\u00B0 CODE \u00BB).
     */
    private String normalizeLabel(String s) {
        return s.trim().toLowerCase().replaceAll("[\\s\\u00A0\\u202F]+", "");
    }

    /**
     * Noms des colonnes, composés sur {@code span} lignes à partir de {@code headerRow} : pour chaque
     * colonne on retient la <strong>dernière</strong> valeur non vide de l'intervalle. Une cellule
     * fusionnée ne porte sa valeur que dans sa 1re cellule : la ligne la plus précise (la plus basse)
     * l'emporte donc sur l'intitulé chapeau (ex. « 1er trimestre » plutôt que « RECOUVREMENT… »).
     */
    private List<String> readHeaders(Sheet sheet, int headerRow, int span) {
        int width = 0;
        for (int r = headerRow; r < headerRow + span; r++) {
            Row row = sheet.getRow(r);
            if (row != null) width = Math.max(width, row.getLastCellNum());
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < width; c++) {
            String name = "";
            for (int r = headerRow; r < headerRow + span; r++) {
                String v = cell(sheet.getRow(r), c);
                if (!v.isEmpty()) name = v;                  // la ligne la plus basse précise l'intitulé
            }
            headers.add(name);
        }
        while (!headers.isEmpty() && headers.get(headers.size() - 1).isEmpty())
            headers.remove(headers.size() - 1);              // retire les colonnes de fin vides
        disambiguateDuplicates(headers);                     // ex. 2 colonnes "%" → "%", "% (2)"
        return headers;
    }

    /**
     * Rend les en-têtes non vides uniques en suffixant les doublons («&nbsp;% » → «&nbsp;% », «&nbsp;% (2) »).
     * Nécessaire pour les feuilles à colonnes homonymes (ex. M.IV.IMPAYE) : sans cela, les valeurs
     * seraient écrasées dans la {@code Map} colonne→valeur.
     */
    private void disambiguateDuplicates(List<String> headers) {
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.isEmpty()) continue;
            int n = seen.merge(h, 1, Integer::sum);
            if (n > 1) headers.set(i, h + " (" + n + ")");
        }
    }

    private List<Map<String, String>> readDataRows(Sheet sheet, int headerRow, List<String> headers,
                                                  SheetLayout layout) {
        String marker = normalizeLabel(layout.headerMarker());
        List<Map<String, String>> rows = new ArrayList<>();
        FormulaEvaluator evaluateur = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        int perimees = 0;                   // formules dont le résultat enregistré ne valait plus rien
        String premierePerimee = null;
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlank(row, headers.size())) {
                if (!layout.skipBlankRows()) break;                        // fin des données
                continue;                                                  // feuille aérée : on poursuit
            }
            if (cell(row, 0).toUpperCase().contains("CONTROLE")) break;    // bloc de contrôle
            if (finDeTableau(row, layout)) break;                          // début d'un AUTRE tableau
            if (layout.skipBlankRows() && normalizeLabel(cell(row, 0)).equals(marker))
                continue;                                                  // en-tête répété d'un sous-bloc
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c).isEmpty() ? "col" + c : headers.get(c);
                String valeur = cell(row, c, evaluateur);
                map.put(key, valeur);
                if (cachePerime(row, c, valeur)) {
                    perimees++;
                    if (premierePerimee == null) premierePerimee = row.getCell(c).getAddress().formatAsString();
                }
            }
            rows.add(map);
        }
        if (perimees > 0) {
            log.warn("Feuille {} : {} formule(s) dont le résultat enregistré était périmé (première : {}) — "
                            + "recalculées à la lecture. Ce classeur n'a pas été enregistré par un tableur "
                            + "qui rafraîchit ses formules.",
                    sheet.getSheetName(), perimees, premierePerimee);
        }
        return rows;
    }

    /**
     * Vrai si le résultat enregistré avec la formule ne correspond pas à la valeur recalculée.
     * <p>Le signaler a un intérêt propre : cela désigne un classeur qui n'a pas été rafraîchi par son
     * tableur, donc un fichier dont les montants ne valent que par notre recalcul — et le jour où ce
     * recalcul échouera sur une fonction inconnue de POI, ce sont les zéros du modèle qui partiront.
     */
    private boolean cachePerime(Row row, int c, String valeurLue) {
        Cell cellule = (row == null) ? null : row.getCell(c);
        if (cellule == null || cellule.getCellType() != CellType.FORMULA) return false;
        if (cellule.getCachedFormulaResultType() != CellType.NUMERIC) return false;
        CellStyle style = cellule.getCellStyle();
        String enregistre = formatter.formatRawCellContents(cellule.getNumericCellValue(),
                style.getDataFormat(), style.getDataFormatString()).trim();
        return !enregistre.equals(valeurLue);
    }

    /**
     * Vrai si la ligne ouvre le <strong>tableau suivant</strong> de la feuille — quand la forme en
     * déclare un ({@code endMarker}).
     * <p>À distinguer de l'en-tête <em>répété</em> d'un même tableau, que {@code skipBlankRows}
     * enjambe pour poursuivre : ici le tableau qui commence est d'un autre type, et le lire à la
     * suite du premier mêlerait deux natures de données (les charges parmi les produits).
     */
    private boolean finDeTableau(Row row, SheetLayout layout) {
        if (layout.endMarker() == null) return false;
        String premier = firstNonEmpty(row);
        return premier != null && normalizeLabel(premier).equals(normalizeLabel(layout.endMarker()));
    }

    private SheetMetadata readMetadata(Sheet sheet, int headerRow) {
        String inst = null, code = null, date = null, rapport = null;
        for (int r = 0; r < headerRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String label = firstNonEmpty(row), value = lastNonEmpty(row);
            if (label == null || value == null || label.equals(value)) continue;
            String l = label.toLowerCase();
            if (l.contains("nom de l'institution")) inst = value;
            else if (l.contains("code de l'institution")) code = value;
            else if (l.contains("date des rapports")) date = value;
            else if (l.contains("rapport")) rapport = value;
        }
        return new SheetMetadata(inst, code, date, rapport);
    }

    /** Libellé de la ligne de métadonnées qui porte la date d'arrêté du classeur. */
    private static final String LIBELLE_DATE_RAPPORTS = "datedesrapports";   // compare a normalizeLabel

    /** Profondeur de recherche des métadonnées : elles tiennent dans les toutes premières lignes. */
    private static final int LIGNES_METADONNEES = 15;

    /** Formats de date rencontrés quand la cellule est du texte et non une vraie date Excel. */
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd"));

    /**
     * <strong>Date des rapports</strong> déclarée par le classeur, cherchée dans les métadonnées de
     * ses feuilles (première trouvée).
     * <p>But : dans le workflow Alfresco, personne — ni l'admin qui dépose, ni le contrôleur, ni le
     * validateur — ne saisit de période. Le classeur, lui, porte son propre arrêté ; c'est la seule
     * source fiable de {@code dateArrete} quand la demande n'en donne pas.
     * <p>Accepte une vraie cellule de date Excel comme une date écrite en texte
     * («&nbsp;31/03/2019&nbsp;»), et suit la formule quand la cellule en est une.
     */
    public Optional<LocalDate> dateDesRapports(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            int derniere = Math.min(sheet.getLastRowNum(), LIGNES_METADONNEES);
            for (int r = 0; r <= derniere; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String label = firstNonEmpty(row);
                if (label == null || !normalizeLabel(label).contains(LIBELLE_DATE_RAPPORTS)) continue;
                Optional<LocalDate> date = dateDeLaLigne(row);
                if (date.isPresent()) return date;
            }
        }
        return Optional.empty();
    }

    /** Date portée par une ligne de métadonnées : on lit depuis la droite, la valeur suit le libellé. */
    private Optional<LocalDate> dateDeLaLigne(Row row) {
        for (int c = row.getLastCellNum() - 1; c >= 0; c--) {
            Cell cellule = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cellule == null) continue;
            CellType type = (cellule.getCellType() == CellType.FORMULA)
                    ? cellule.getCachedFormulaResultType()
                    : cellule.getCellType();
            if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellule)) {
                return Optional.of(DateUtil.getLocalDateTime(cellule.getNumericCellValue()).toLocalDate());
            }
            Optional<LocalDate> texte = parseDate(cell(row, c));
            if (texte.isPresent()) return texte;
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> parseDate(String valeur) {
        String v = valeur.trim();
        if (v.isEmpty()) return Optional.empty();
        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return Optional.of(LocalDate.parse(v, format));
            } catch (DateTimeParseException ignore) {
                // format suivant
            }
        }
        return Optional.empty();
    }

    // ---- utilitaires cellule (tout en texte, cellules vides gérées) ----
    private String cell(Row row, int c) {
        return cell(row, c, null);
    }

    private String cell(Row row, int c, FormulaEvaluator evaluateur) {
        if (row == null) return "";
        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) return formulaValue(cell, evaluateur);
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * Valeur d'une cellule de formule, jamais le texte de la formule — celui-ci descendrait jusqu'au
     * mapping, qui le refuserait comme montant non numérique.
     *
     * <p>Un tableur enregistre le résultat de ses formules à côté d'elles, et c'est ce résultat qu'on
     * veut. Mais il peut être <strong>périmé</strong> : un classeur rempli par un script (openpyxl)
     * ou exporté d'un autre outil garde les formules du modèle sans jamais les recalculer, et
     * conserve donc les zéros du modèle vide. Toute une colonne part alors à zéro — « Montant net »
     * du bilan, qui n'existe qu'en formule, en est le cas type.
     *
     * <p>D'où le recalcul, dès qu'un évaluateur est fourni (lecture des lignes de données). On ne
     * retombe sur le résultat enregistré que si le recalcul échoue, POI ne connaissant pas toutes les
     * fonctions d'Excel : une valeur périmée vaut mieux que pas de valeur.
     *
     * <p>Cellule en erreur (#DIV/0!, #REF!) → vide, traitée comme une case non remplie.
     */
    private String formulaValue(Cell cell, FormulaEvaluator evaluateur) {
        CellStyle style = cell.getCellStyle();
        CellValue calcule = recalcule(cell, evaluateur);
        if (calcule != null) {
            return switch (calcule.getCellType()) {
                case NUMERIC -> formatter.formatRawCellContents(
                        calcule.getNumberValue(), style.getDataFormat(), style.getDataFormatString()).trim();
                case STRING -> calcule.getStringValue().trim();
                case BOOLEAN -> calcule.getBooleanValue() ? "TRUE" : "FALSE";
                default -> "";
            };
        }
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC -> formatter.formatRawCellContents(
                    cell.getNumericCellValue(), style.getDataFormat(), style.getDataFormatString()).trim();
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> "";
        };
    }

    /** Résultat recalculé de la formule, ou {@code null} si POI ne sait pas l'évaluer. */
    private CellValue recalcule(Cell cell, FormulaEvaluator evaluateur) {
        if (evaluateur == null) return null;
        try {
            return evaluateur.evaluate(cell);
        } catch (RuntimeException e) {              // fonction inconnue de POI, référence externe…
            return null;
        }
    }

    private boolean isBlank(Row row, int width) {
        if (row == null) return true;
        for (int c = 0; c < Math.max(width, 1); c++) if (!cell(row, c).isEmpty()) return false;
        return true;
    }

    private String firstNonEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) { String v = cell(row, c); if (!v.isEmpty()) return v; }
        return null;
    }

    private String lastNonEmpty(Row row) {
        String found = null;
        for (int c = 0; c < row.getLastCellNum(); c++) { String v = cell(row, c); if (!v.isEmpty()) found = v; }
        return found;
    }
}