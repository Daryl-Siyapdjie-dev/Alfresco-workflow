package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.ConcoursConsentisImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code PRUD_05} (concours consentis aux mandataires et actionnaires) → items
 * {@link ConcoursConsentisImf} (endpoint {@code /api/imf/prud/prud_05}).
 * <p>Colonnes : {@code N°CODE→code}, {@code N°→numero},
 * {@code Nom ou raison sociale du bénéficiaire→nomBeneficiaire},
 * {@code Montant des concours*→montantConcours},
 * {@code Provisions comptabilisées→provisionsComptabilisées},
 * {@code Montant Net des Concours accordés→montantNetConcours}.
 * <p><strong>Ligne retenue = ligne portant un code.</strong> C'est ce qui écarte, sans les énumérer,
 * les titres de section (« NORMES DE SOLVABILITE »), les lignes de numérotation des colonnes
 * (« 1 | 2=1/N9 | 3=1-N11 ») et les sous-titres « A inclure » / « A déduire » : aucune ne décrit un
 * poste transmissible, et SIRYF valide les codes de ligne (« Code X est introuvable »).
 * <p>Les trois colonnes de montant portent des renvois vers IGEC (« IGEC17_AE17 ») ou une condition
 * en clair (« Si W19>R13 ; W19-R13 ») : consignes de remplissage, traitées comme des cases vides.
 */
@Component
public class PrudConcoursMandatairesMapper implements SheetMapper<ConcoursConsentisImf> {

    static final String CODE = "N°CODE";
    static final String NUMERO = "N°";
    static final String NOM = "Nom ou raison sociale du bénéficiaire";
    static final String CONCOURS = "Montant des concours*";
    static final String PROVISIONS = "Provisions comptabilisées";
    static final String NET = "Montant Net des Concours accordés";

    @Override
    public List<ConcoursConsentisImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NUMERO, NOM, CONCOURS, PROVISIONS, NET);
        List<ConcoursConsentisImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // titre de section, numérotation, note de bas de feuille
            items.add(new ConcoursConsentisImf()
                    .code(code)
                    .numero(cols.get(row, NUMERO))
                    .nomBeneficiaire(cols.get(row, NOM))
                    .montantConcours(Numbers.parseMontantOuRenvoi(cols.get(row, CONCOURS), CONCOURS))
                    .provisionsComptabilisées(Numbers.parseMontantOuRenvoi(cols.get(row, PROVISIONS), PROVISIONS))
                    .montantNetConcours(Numbers.parseMontantOuRenvoi(cols.get(row, NET), NET)));
        }
        return items;
    }
}
