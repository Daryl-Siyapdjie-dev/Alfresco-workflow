package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CompositionCapitalImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_06} (composition du capital) → items
 * {@link CompositionCapitalImf} (endpoint {@code /api/imf/igec/igec06}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p><strong>Deux écarts assumés</strong>, à porter à la matrice de correspondance :
 * <ul>
 *   <li>le template donne, sous « SOUSCRIT » comme sous « LIBERE », un <em>montant</em> et un
 *       <em>pourcentage de détention</em> ; le DTO, lui, attend un montant et un montant <em>en
 *       GNF</em>. Les deux montants sont donc transmis, {@code montantCapitalSouscritGnf} et
 *       {@code montantCapitalLibereGnf} restent vides : il n'y a pas de colonne GNF dans la source ;</li>
 *   <li>les deux colonnes de pourcentage n'ont aucun champ cible et ne sont pas transmises.</li>
 * </ul>
 */
@Component
public class IgecCapitalMapper implements SheetMapper<CompositionCapitalImf> {

    static final String CODE = "N°CODE";
    static final String ACTIONNAIRE = "ACTIONNAIRES";
    static final String NATIONALITE = "NATIONNALITE";
    static final String SOUSCRIT = "SOUSCRIT";
    static final String LIBERE = "LIBERE";

    @Override
    public List<CompositionCapitalImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), ACTIONNAIRE, SOUSCRIT, LIBERE);
        List<CompositionCapitalImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CompositionCapitalImf()
                    .code(code)
                    .actionnaire(cols.get(row, ACTIONNAIRE))
                    .nationnalite(cols.get(row, NATIONALITE))
                    .montantCapitalSouscrit(Numbers.parseMontantOuRenvoi(cols.get(row, SOUSCRIT), SOUSCRIT))
                    .montantCapitalLibere(Numbers.parseMontantOuRenvoi(cols.get(row, LIBERE), LIBERE)));
        }
        return items;
    }
}
