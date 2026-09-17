package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CoefficientObservationLiquiditeImmediate;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code PRUD_02} (coefficient d'observation de liquidité immédiate) → items
 * {@link CoefficientObservationLiquiditeImmediate} (endpoint {@code /api/imf/prud/prud_02}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N° compte→numCompte},
 * {@code Numérateur - Réalisables→libelle}, {@code Encours bruts→encoursBrut}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p><strong>Feuille en deux blocs</strong> (« Numérateur - Réalisables » puis « Dénominateur :
 * Exigibles », mêmes colonnes) : le second rouvre son en-tête par « N° CODE » (avec une espace), que
 * le lecteur reconnaît comme un en-tête répété et ignore. Les lignes du 2e bloc retombent donc sur
 * les intitulés du 1er et partent dans <strong>un seul envoi</strong> — comme SITU_01/SITU_02.
 */
@Component
public class PrudLiquiditeImmediateMapper implements SheetMapper<CoefficientObservationLiquiditeImmediate> {

    static final String CODE = "N°CODE";
    static final String NUM_COMPTE = "N° compte";
    static final String LIBELLE = "Numérateur - Réalisables";
    static final String ENCOURS = "Encours bruts";

    @Override
    public List<CoefficientObservationLiquiditeImmediate> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUM_COMPTE, LIBELLE, ENCOURS);
        List<CoefficientObservationLiquiditeImmediate> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new CoefficientObservationLiquiditeImmediate()
                    .code(code)
                    .numCompte(cols.get(row, NUM_COMPTE))
                    .libelle(cols.get(row, LIBELLE))
                    .encoursBrut(Numbers.parseMontantOuRenvoi(cols.get(row, ENCOURS), ENCOURS)));
        }
        return items;
    }
}
