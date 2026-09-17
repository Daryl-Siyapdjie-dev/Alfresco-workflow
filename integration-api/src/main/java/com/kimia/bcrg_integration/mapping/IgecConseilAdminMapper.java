package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CompositionConseilAdmin;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_05} (composition du conseil d'administration) → items
 * {@link CompositionConseilAdmin} (endpoint {@code /api/imf/igec/igec05}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 */
@Component
public class IgecConseilAdminMapper implements SheetMapper<CompositionConseilAdmin> {

    static final String CODE = "N°CODE";
    static final String NOM = "NOM ET PRENOMS DES ADMINISTRATEURS";
    static final String REPRESENTE = "INSTITUTION OU PERSONNE REPRESENTEE";
    static final String NATIONALITE = "NATIONALITE";
    static final String FONCTION = "FONCTION AU SEIN DU CA";
    static final String AUTORISATION = "Référence de l'autorisation d'exercice de la fonction";
    static final String CONTACT = "CONTACTS";
    static final String EMAIL = "EMAIL";

    @Override
    public List<CompositionConseilAdmin> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NOM, REPRESENTE, NATIONALITE, FONCTION);
        List<CompositionConseilAdmin> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CompositionConseilAdmin()
                    .code(code)
                    .nomPrenomAdministrateur(cols.get(row, NOM))
                    .institutionPersonneRepresentee(cols.get(row, REPRESENTE))
                    .nationalite(cols.get(row, NATIONALITE))
                    .fonctionConseilAdministration(cols.get(row, FONCTION))
                    .referenceAutorisationExerciceFonction(cols.get(row, AUTORISATION))
                    .contact(cols.get(row, CONTACT))
                    .email(cols.get(row, EMAIL)));
        }
        return items;
    }
}
