package com.kimia.bcrg_integration.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Le résultat qu'un tableur enregistre à côté d'une formule peut être <strong>périmé</strong> :
 * un classeur rempli par un script garde les formules du modèle sans les recalculer, et conserve
 * donc les zéros du modèle vide. La colonne « Montant net » du bilan, qui n'existe qu'en formule,
 * partait ainsi entièrement à zéro alors que l'écran affichait les bons montants.
 *
 * <p>Ces tests figent le recalcul à la lecture. La différence est invisible à l'œil nu — dans les
 * deux classeurs, Excel affiche le même nombre.
 */
class FormulePerimeeTest {

    private final ExcelReader reader = new ExcelReader();

    /**
     * Un bilan d'une ligne : brut et amortissement saisis, net en formule.
     *
     * @param resultatEnregistre ce que le tableur a mémorisé comme résultat du net — {@code 0} pour
     *                           un classeur non rafraîchi, {@code 700} pour un enregistrement à jour
     */
    private Workbook classeur(double resultatEnregistre) {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("M.I.BILAN");
        Row entete = s.createRow(0);
        entete.createCell(0).setCellValue("CODE");
        entete.createCell(1).setCellValue("Montant brut");
        entete.createCell(2).setCellValue("Amortissement & Provisions");
        entete.createCell(3).setCellValue("Montant net");

        Row ligne = s.createRow(1);
        ligne.createCell(0).setCellValue("M.I.B.A.101");
        ligne.createCell(1).setCellValue(1000);
        ligne.createCell(2).setCellValue(300);
        Cell net = ligne.createCell(3);
        net.setCellFormula("B2-C2");
        net.setCellValue(resultatEnregistre);
        return wb;
    }

    private String montantNet(Workbook wb) {
        Map<String, String> ligne = reader.readSheet(wb, "M.I.BILAN").rows().get(0);
        return ligne.get("Montant net");
    }

    @Test
    void un_resultat_perime_est_recalcule() throws Exception {
        try (Workbook wb = classeur(0)) {                 // le zéro hérité du modèle vide
            assertEquals("700", montantNet(wb), "le net doit valoir brut - amortissement");
        }
    }

    @Test
    void un_resultat_a_jour_est_conserve() throws Exception {
        try (Workbook wb = classeur(700)) {
            assertEquals("700", montantNet(wb));
        }
    }

    /** Le recalcul vaut aussi pour les lignes de total, dont le brut est lui-même une formule. */
    @Test
    void un_total_en_formule_est_recalcule() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("M.I.BILAN");
            Row entete = s.createRow(0);
            entete.createCell(0).setCellValue("CODE");
            entete.createCell(1).setCellValue("Montant brut");

            Row ligne = s.createRow(1);
            ligne.createCell(0).setCellValue("M.I.B.A.101");
            ligne.createCell(1).setCellValue(1200);

            Row total = s.createRow(2);
            total.createCell(0).setCellValue("M.I.B.A.TA");
            Cell somme = total.createCell(1);
            somme.setCellFormula("SUM(B2:B2)");
            somme.setCellValue(0);                        // total jamais rafraîchi

            assertEquals("1200", reader.readSheet(wb, "M.I.BILAN").rows().get(1).get("Montant brut"));
        }
    }
}
