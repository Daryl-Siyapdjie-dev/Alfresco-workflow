package com.kimia.bcrg_integration.persistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Journal <strong>append-only</strong> sur disque : une ligne par fait, jamais de réécriture.
 *
 * <p><strong>Pourquoi un fichier et non une base.</strong> Ce module est un connecteur : il n'a
 * aujourd'hui aucune base de données, et en introduire une pour deux journaux ferait porter au
 * déploiement une dépendance qu'il n'a pas. Un journal en append-only est par ailleurs la forme
 * naturelle d'une piste d'audit (§9) — on y ajoute, on n'y corrige pas — et il reste lisible à
 * l'œil nu le jour où quelqu'un doit reconstituer une transmission.
 *
 * <p><strong>Une écriture qui échoue ne fait pas échouer la transmission.</strong> Quand ce journal
 * est appelé, l'envoi a <em>déjà eu lieu</em> : lever ici transformerait une transmission réussie en
 * échec rapporté, ce qui serait un mensonge plus grave que la ligne perdue. L'incident part donc en
 * {@code ERROR} — visible en supervision — et le traitement continue.
 */
public final class JournalFichier {

    private static final Logger log = LoggerFactory.getLogger(JournalFichier.class);

    private final Path fichier;

    public JournalFichier(Path fichier) {
        this.fichier = fichier;
        try {
            Path parent = fichier.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            log.error("Journal {} : impossible de créer le dossier — les écritures seront perdues",
                    fichier, e);
        }
    }

    /** Ajoute une ligne en fin de journal. Synchronisé : plusieurs feuilles peuvent partir de front. */
    public synchronized void ajouter(String ligne) {
        try {
            Files.writeString(fichier, ligne + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Journal {} : écriture impossible — la ligne est perdue : {}", fichier, ligne, e);
        }
    }

    /** Lignes du journal, dans l'ordre d'écriture. Journal absent ou illisible → liste vide. */
    public List<String> lignes() {
        if (!Files.exists(fichier)) {
            return List.of();
        }
        try {
            return Files.readAllLines(fichier, StandardCharsets.UTF_8).stream()
                    .filter(l -> !l.isBlank())
                    .toList();
        } catch (IOException e) {
            log.error("Journal {} : lecture impossible — repart d'un journal vide", fichier, e);
            return List.of();
        }
    }

    public Path fichier() {
        return fichier;
    }
}
