package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfInformationGenerale;
import com.kimia.bcrg_integration.excel.ExcelReader;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide la tranche verticale lecture → mapping pour SIGIMF {@code M.0.INFO.G}, 100 % locale.
 * Ancré sur le fichier réel {@code src/test/resources/samples/SIGIMF.xlsx} (institution YETEMALI).
 */
class SigImfInfoGeneraleMapperTest {

    private final ExcelReader reader = new ExcelReader();
    private final SigImfInfoGeneraleMapper mapper = new SigImfInfoGeneraleMapper();

    private Workbook openSample() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("samples/SIGIMF.xlsx");
        assertNotNull(in, "Échantillon samples/SIGIMF.xlsx absent du classpath de test");
        return WorkbookFactory.create(in);
    }

    @Test
    void mappe_les_9_lignes_vers_des_SigImfInformationGenerale() throws Exception {
        try (Workbook wb = openSample()) {
            SheetTable t = reader.readSheet(wb, "M.0.INFO.G");
            List<SigImfInformationGenerale> items = mapper.map(t);

            assertEquals(9, items.size(), "9 lignes attendues");

            // première ligne : M.0.LS / Statut légal / SA
            assertEquals("M.0.LS", items.get(0).getCode());
            assertEquals("SA", items.get(0).getDonnee());

            // valeur ASCII repérable (évite les pièges d'encodage sur les accents)
            SigImfInformationGenerale email = items.stream()
                    .filter(i -> "M.0.EM".equals(i.getCode()))
                    .findFirst().orElseThrow();
            assertEquals("cpecg@yetemali-gn.com", email.getDonnee());

            // invariant : tout item a un code non vide
            assertTrue(items.stream().allMatch(i -> i.getCode() != null && !i.getCode().isBlank()));
        }
    }

    @Test
    void signale_une_colonne_obligatoire_absente() {
        SheetTable bad = new SheetTable("X", null, List.of("AUTRE"), List.of());
        MappingException ex = org.junit.jupiter.api.Assertions.assertThrows(
                MappingException.class, () -> mapper.map(bad));
        assertTrue(ex.getMessage().contains("CODE"), ex.getMessage());
    }
}
