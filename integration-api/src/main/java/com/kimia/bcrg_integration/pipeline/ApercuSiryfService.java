package com.kimia.bcrg_integration.pipeline;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relit chez SIRYF ce qu'il détient réellement pour un fichier et une date d'arrêté, et rend un
 * classeur Excel d'aperçu. C'est la contre-partie de la transmission : après un envoi, la seule
 * preuve immédiate est la réponse HTTP ; cet aperçu montre ce que SIRYF a effectivement conservé
 * (« je ne vois aucune transmission » ⇒ on ouvre l'aperçu).
 *
 * <p>Portage en Java de l'outil de recette {@code rapport-siryf.py}. L'endpoint « Impression des
 * feuilles transmises » exige {@code codeFeuilles} : sans cette liste, il rend un classeur SANS
 * feuille — indiscernable d'une absence de données. On interroge donc <strong>une feuille à la
 * fois</strong> ({@code LOT=1}) : certaines feuilles font tomber le serveur en 500, et un lot entier
 * échoue dès qu'une seule feuille le fait ; une par une, un refus ne coûte que sa propre feuille.
 */
@Service
public class ApercuSiryfService {

    private static final Logger log = LoggerFactory.getLogger(ApercuSiryfService.class);

    /** Endpoint d'impression par fichier (SIGIMF a le sien, les autres vivent sous {@code /etats/imf/}). */
    private static final Map<String, String> ENDPOINTS = Map.of(
            "SIGIMF", "/etats/sigimf/rapport",
            "SITU", "/etats/imf/situ/rapport",
            "PRUD", "/etats/imf/prud/rapport",
            "IGEC", "/etats/imf/igec/rapport",
            "FINS", "/etats/imf/fins/rapport");

    /** Feuilles connues par fichier — sans elles, l'endpoint rend un classeur vide. */
    private static final Map<String, List<String>> FEUILLES = Map.of(
            "SIGIMF", List.of("M.0.INFO.G", "M.I.BILAN", "M.II.RESULTAT", "M.III.HS_BILAN",
                    "M.IV.IMPAYE", "M.V.PFC", "M.VI.ADM.DIR", "M.VII.PERSONNEL", "M.VIII.DIX.DEB",
                    "M.IX.SG", "M.X.RSL", "M.XI.RPCS", "M.XII.RCS", "M.XIII.OATA", "M.XIV.FS",
                    "M.XV.RECAP", "M.XVI.CERS", "M.XVII.PROV", "M.XVIII.DIX.CREANCIERS",
                    "M.XIX.CREDITS.RESTRUCTURES", "M.XX.DIX.BENEF.SIGN", "M.XXI.BALANCES_AGEES",
                    "M.XXII.INDIC.PRUDENTIELS", "M.XXII.RATIO.PRUDENTIELS",
                    "TAB_AUTRES_INDIC_1", "TAB_AUTRES_INDIC_2"),
            "SITU", List.of("SITU_01", "SITU_02", "SITU_03"),
            "PRUD", List.of("ECNP", "FPNC", "FPN", "APR", "PRUD_01", "PRUD_02", "PRUD_03",
                    "PRUD_04", "PRUD_05", "PRUD_06", "IMAO_ICD", "IMAO_INCD"),
            "IGEC", List.of("IGEC_01", "IGEC_02", "IGEC_03", "IGEC_04", "IGEC_05", "IGEC_06",
                    "IGEC_07", "IGEC_08", "IGEC_09", "IGEC_10", "IGEC_11", "IGEC_12", "IGEC_13",
                    "IGEC_14", "IGEC_15", "IGEC_16", "IGEC_17"),
            "FINS", List.of("FINS_01", "FINS_02", "FINS_03", "FINS_04", "FINS_07_GNF", "FINS_08_GNF",
                    "FINS_09", "FINS_12", "FINS_14", "FINS_15", "FINS_16", "FINS_17"));

    private final RestClient client;

    public ApercuSiryfService(RestClient bcrgAuthenticatedRestClient) {
        this.client = bcrgAuthenticatedRestClient;
    }

    public boolean supporte(String codeFichier) {
        return codeFichier != null && ENDPOINTS.containsKey(codeFichier.toUpperCase());
    }

    /**
     * Construit le classeur d'aperçu : une feuille par feuille détenue par SIRYF, valeurs seules.
     * Les feuilles que SIRYF refuse d'imprimer (500) sont ignorées sans faire échouer l'ensemble.
     */
    public byte[] apercu(String codeFichier, LocalDate dateArrete) {
        String cf = codeFichier == null ? "" : codeFichier.toUpperCase();
        String endpoint = ENDPOINTS.get(cf);
        if (endpoint == null) {
            throw new IllegalArgumentException("Aperçu non disponible pour le fichier « " + codeFichier
                    + " » (connus : " + String.join(", ", ENDPOINTS.keySet()) + ")");
        }
        int annee = dateArrete.getYear();
        String arrete = dateArrete + "T00:00:00Z";

        try (XSSFWorkbook combine = new XSSFWorkbook()) {
            int detenues = 0;
            for (String feuille : FEUILLES.get(cf)) {
                Map<String, Object> corps = new LinkedHashMap<>();
                corps.put("codeFichier", cf);
                corps.put("codeFeuilles", List.of(feuille));
                corps.put("dateArrete", arrete);
                corps.put("annee", annee);
                corps.put("exercice", annee);

                byte[] octets;
                try {
                    octets = client.post().uri(endpoint).body(corps).retrieve().body(byte[].class);
                } catch (Exception e) {
                    log.warn("Aperçu {} : feuille {} refusée par SIRYF ({})", cf, feuille,
                            e.getMessage() == null ? e.toString() : e.getMessage());
                    continue;   // 500 sur une feuille : elle ne doit pas emporter tout l'aperçu
                }
                if (octets == null || octets.length == 0) {
                    continue;
                }
                try (XSSFWorkbook source = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
                    for (int i = 0; i < source.getNumberOfSheets(); i++) {
                        copierFeuille(source.getSheetAt(i), combine);
                        detenues++;
                    }
                } catch (Exception e) {
                    log.warn("Aperçu {} : lecture du classeur rendu pour {} impossible ({})", cf,
                            feuille, e.toString());
                }
            }
            log.info("Aperçu {} @ {} : {} feuille(s) détenue(s) par SIRYF", cf, dateArrete, detenues);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            combine.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Échec de construction du classeur d'aperçu " + cf, e);
        }
    }

    /** Copie une feuille (valeurs seules) dans le classeur combiné ; nom tronqué à 31 car. (limite Excel). */
    private static void copierFeuille(Sheet origine, XSSFWorkbook dest) {
        String nom = origine.getSheetName();
        if (nom.length() > 31) {
            nom = nom.substring(0, 31);
        }
        Sheet cible = dest.getSheet(nom);
        if (cible == null) {
            cible = dest.createSheet(nom);
        }
        for (Row ligne : origine) {
            Row cibleLigne = cible.getRow(ligne.getRowNum());
            if (cibleLigne == null) {
                cibleLigne = cible.createRow(ligne.getRowNum());
            }
            for (Cell cellule : ligne) {
                copierValeur(cellule, cibleLigne.createCell(cellule.getColumnIndex()));
            }
        }
    }

    /** Recopie la valeur d'une cellule (formule = résultat mis en cache), pour un aperçu figé. */
    private static void copierValeur(Cell src, Cell dst) {
        CellType type = src.getCellType() == CellType.FORMULA ? src.getCachedFormulaResultType()
                : src.getCellType();
        switch (type) {
            case STRING -> dst.setCellValue(src.getStringCellValue());
            case NUMERIC -> dst.setCellValue(src.getNumericCellValue());
            case BOOLEAN -> dst.setCellValue(src.getBooleanCellValue());
            case BLANK -> { /* rien */ }
            default -> {
                try {
                    dst.setCellValue(src.toString());
                } catch (Exception ignore) {
                    /* cellule illisible : ignorée */
                }
            }
        }
    }
}
