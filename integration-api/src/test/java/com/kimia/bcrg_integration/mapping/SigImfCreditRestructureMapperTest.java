package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.SigImfCreditRestructure;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Valide le mapping <em>partiel</em> M.XIX.CREDITS.RESTRUCTURES → {@link SigImfCreditRestructure} :
 * seuls les champs à correspondance sûre sont mappés ; les colonnes géographiques et les champs DTO
 * sans source restent non renseignés (à compléter via la matrice).
 */
class SigImfCreditRestructureMapperTest {

    private final SigImfCreditRestructureMapper mapper = new SigImfCreditRestructureMapper();

    private static final List<String> HEADERS = List.of(
            "N°.", "Nom et prénoms", "Montant original", "Volume de financement des crédits",
            "Quartier", "Répartition du Volume de financement des crédits par secteur d'activités",
            "Solde", "Retard en capital", "Ville", "Prêts à moyen et long terme ( 2 ans et +)",
            "Commune", "Garantie");

    @Test
    void mappe_les_champs_surs_et_laisse_le_reste_null() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("N°.", "1");
        r.put("Nom et prénoms", "Ent. Gamma");
        r.put("Montant original", "100 000");
        r.put("Volume de financement des crédits", "ignoré");
        r.put("Quartier", "Kaloum");
        r.put("Répartition du Volume de financement des crédits par secteur d'activités", "Commerce");
        r.put("Solde", "90 000");
        r.put("Retard en capital", "1 000");
        r.put("Ville", "Conakry");
        r.put("Prêts à moyen et long terme ( 2 ans et +)", "oui");
        r.put("Commune", "Kaloum");
        r.put("Garantie", "25 000");

        List<SigImfCreditRestructure> items =
                mapper.map(new SheetTable("M.XIX.CREDITS.RESTRUCTURES", null, HEADERS, List.of(r)));

        assertEquals(1, items.size());
        SigImfCreditRestructure it = items.get(0);
        assertEquals(1, it.getNumero());
        assertEquals("Ent. Gamma", it.getNomEtPrenoms());
        assertEquals(100000.0, it.getMontantOriginal(), 1e-9);
        assertEquals(90000.0, it.getSolde(), 1e-9);
        assertEquals(1000.0, it.getRetardCapital(), 1e-9);
        assertEquals(25000.0, it.getGarantie(), 1e-9);
        // champs sans colonne source → null
        assertNull(it.getDateRestructuration());
        assertNull(it.getMontantRestructure());
        assertNull(it.getNombreJoursRetard());
    }
}
