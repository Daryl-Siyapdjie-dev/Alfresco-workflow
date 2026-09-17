package com.kimia.bcrg_integration.mapping;

import com.kimia.bcrg_integration.client.dto.CompositionDirectionImf;
import com.kimia.bcrg_integration.excel.SheetTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper de la feuille {@code IGEC_04} (composition de la direction) → items
 * {@link CompositionDirectionImf} (endpoint {@code /api/imf/igec/igec04}).
 * <p><strong>Ligne retenue = ligne portant un code.</strong> Règle commune aux 17 feuilles :
 * elle écarte la ligne qui numérote les colonnes et les restes d'en-tête que la lecture large
 * ramène, sans avoir à les énumérer.
 * <p>La colonne « DATE ET NUMERO DE L'ACTE DE NOMINATION » mêle deux informations dont une seule a
 * un champ cible : on en <strong>extrait la date</strong> ({@link Dates#premiereDate}) et le numéro
 * de l'acte n'est pas transmis — écart à porter à la matrice de correspondance.
 */
@Component
public class IgecDirectionMapper implements SheetMapper<CompositionDirectionImf> {

    static final String CODE = "N°CODE";
    static final String NOM = "NOM ET PRENOMS DES DIRIGEANTS";
    static final String NATIONALITE = "NATIONALITE";
    static final String FONCTION = "FONCTIONS";
    static final String ACTE = "DATE ET NUMERO DE L'ACTE DE NOMINATION";
    static final String AUTORISATION = "Référence Agrément ou ou autorisation";
    static final String CONTACT = "CONTACTS";
    static final String EMAIL = "Email";

    @Override
    public List<CompositionDirectionImf> map(SheetTable sheet) {
        Columns cols = Columns.of(sheet.headers(), NOM, NATIONALITE, FONCTION);
        List<CompositionDirectionImf> items = new ArrayList<>();
        for (Map<String, String> row : sheet.rows()) {
            String code = cols.get(row, CODE);
            if (!Codes.estCodeDeLigne(code)) continue;  // chapeau d'en-tête, numérotation, ligne de service
            items.add(new CompositionDirectionImf()
                    .code(code)
                    .nomPrenomDirigeant(cols.get(row, NOM))
                    .nationalite(cols.get(row, NATIONALITE))
                    .fonction(cols.get(row, FONCTION))
                    .dateNomination(Dates.premiereDate(cols.get(row, ACTE)))
                    .referenceAutorisationExerciceFonction(cols.get(row, AUTORISATION))
                    .contact(cols.get(row, CONTACT))
                    .email(cols.get(row, EMAIL)));
        }
        return items;
    }
}
