package com.kimia.bcrg_integration.excel;

/** En-tête d'une feuille SIRYF (lignes 1-4). reportDate servira de base à dateArrete. */
public record SheetMetadata(String institutionName, String institutionCode,
                            String reportDate, String reportName) {}