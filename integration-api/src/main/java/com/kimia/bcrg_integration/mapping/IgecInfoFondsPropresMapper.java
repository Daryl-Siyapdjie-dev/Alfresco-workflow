package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.InfoComplementairesCalculsFPN;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_17} (informations complémentaires pour le calcul des fonds
 * propres nets) → items {@link InfoComplementairesCalculsFPN} (endpoint
 * {@code /api/imf/igec/igec17}).
 * <p>Seule feuille d'IGEC dont le marqueur d'en-tête est « CODE » et dont l'en-tête tient sur
 * <strong>une</strong> ligne : la suivante ne numérote que les colonnes et écraserait « MONTANT ».
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecInfoFondsPropresMapper implements SheetMapper<InfoComplementairesCalculsFPN> {

    static final String CODE = "CODE";
    static final String LIBELLE = "ELEMENTS D'INFORMATION COMPLEMENTAIRE POUR LE CALCUL DES FONDS PROPRES NETS";
    static final String MONTANT = "MONTANT";

    @Override
    public List<InfoComplementairesCalculsFPN> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), LIBELLE, MONTANT);
        List<InfoComplementairesCalculsFPN> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new InfoComplementairesCalculsFPN()
                    .code(code)
                    .libelle(cols.get(row, LIBELLE))
                    .montant(Numbers.parseMontantOuRenvoi(cols.get(row, MONTANT), MONTANT)));
        }
        return items;
    }
}
