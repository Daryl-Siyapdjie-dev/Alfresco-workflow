package com.kimia.bcrg_integration.excel;

import java.util.List;
import java.util.Map;

/** Contenu exploitable d'une feuille : métadonnées + en-têtes + lignes (colonne → valeur). */
public record SheetTable(String sheetName, SheetMetadata metadata,
                         List<String> headers, List<Map<String, String>> rows) {}