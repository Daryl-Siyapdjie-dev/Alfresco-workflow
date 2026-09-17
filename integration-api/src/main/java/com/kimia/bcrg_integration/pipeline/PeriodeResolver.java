package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.excel.ExcelReader;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Détermine la {@code dateArrete} d'un classeur <strong>quand la demande n'en fournit pas</strong>.
 * <p>Le workflow documentaire (dépôt admin → contrôle → validation) ne demande de période à
 * personne : l'approbation du validateur déclenche l'envoi, sans formulaire. La période doit donc
 * pouvoir venir du classeur lui-même, via la ligne de métadonnées «&nbsp;Date des rapports&nbsp;»
 * que {@link ExcelReader} sait déjà lire.
 * <p>Une période explicite reste prioritaire : cette résolution n'est qu'un repli, et elle échoue
 * en <em>erreur de demande</em> (400) plutôt que de deviner — envoyer une feuille sur la mauvaise
 * période consommerait un couple (feuille, dateArrete) irrécupérable sans autorisation BCRG.
 */
@Component
public class PeriodeResolver {

    private static final Logger log = LoggerFactory.getLogger(PeriodeResolver.class);

    private final ExcelReader reader;

    public PeriodeResolver(ExcelReader reader) {
        this.reader = reader;
    }

    /**
     * Date d'arrêté déclarée par le classeur.
     *
     * @throws IllegalArgumentException si le classeur est illisible ou ne déclare pas de date —
     *         l'appelant doit alors préciser {@code annee} et {@code mois}
     */
    public LocalDate dateArrete(File fichier) {
        Optional<LocalDate> trouvee;
        try (Workbook workbook = WorkbookFactory.create(fichier, null, true)) {
            trouvee = reader.dateDesRapports(workbook);
        } catch (Exception e) {
            throw new IllegalArgumentException("Période absente de la demande et classeur illisible : "
                    + fichier.getName() + " — " + e.getMessage(), e);
        }
        LocalDate date = trouvee.orElseThrow(() -> new IllegalArgumentException(
                "Période absente de la demande et « Date des rapports » introuvable dans "
                        + fichier.getName() + " : préciser « annee » et « mois »."));

        if (!date.equals(YearMonth.from(date).atEndOfMonth())) {
            log.warn("Date des rapports du classeur ({}) : ce n'est pas une fin de mois — "
                    + "SIRYF attend un dernier jour de mois (et une fin de trimestre pour les "
                    + "feuilles trimestrielles). Envoi tenté tel quel.", date);
        }
        log.info("Période non fournie : dateArrete déduite du classeur = {}", date);
        return date;
    }
}
