package com.kimia.bcrg_integration.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Annuaire des {@link JobRegistry} : donne le registre correspondant à un code fichier.
 * <p>C'est le seul endroit qui connaît la liste des fichiers réglementaires pris en charge ;
 * ajouter un fichier (FINS, IGEC, PRUD, BALG) revient donc à déclarer un nouveau {@code @Component}
 * implémentant {@link JobRegistry} — Spring l'injecte ici, sans toucher ni au pipeline ni à l'API.
 */
@Component
public class JobRegistries {

    private final Map<String, JobRegistry> parCode = new LinkedHashMap<>();

    /** {@code @Autowired} explicite : sans lui, Spring hésite entre ce constructeur et la
     *  variante varargs ci-dessous, et cherche un constructeur par défaut qui n'existe pas. */
    @Autowired
    public JobRegistries(List<JobRegistry> registres) {
        for (JobRegistry registre : registres) {
            JobRegistry precedent = parCode.put(normalise(registre.codeFichier()), registre);
            if (precedent != null) {
                throw new IllegalStateException(
                        "Deux registres déclarent le même code fichier : " + registre.codeFichier());
            }
        }
    }

    /** Variante hors Spring (tests, outils en ligne de commande). */
    public JobRegistries(JobRegistry... registres) {
        this(List.of(registres));
    }

    /**
     * Registre du fichier demandé.
     *
     * @throws IllegalArgumentException si le code est inconnu — l'appelant s'est trompé de fichier,
     *         c'est une erreur de demande (400) et non une panne ; le message liste les codes connus.
     */
    public JobRegistry forFile(String codeFichier) {
        JobRegistry registre = codeFichier == null ? null : parCode.get(normalise(codeFichier));
        if (registre == null) {
            throw new IllegalArgumentException(
                    "Fichier réglementaire inconnu : « " + codeFichier + " » (connus : " + codesFichiers() + ")");
        }
        return registre;
    }

    /**
     * Registre correspondant au contenu d'un classeur, d'après ses <strong>noms de feuilles</strong>.
     * <p>But : dans le workflow Alfresco, l'appelant est une règle documentaire — elle n'a aucun
     * moyen fiable de dire si le classeur validé est un SIGIMF, un SITU ou un PRUD. Le classeur, lui,
     * le dit par ses feuilles ({@code M.I.BILAN} vs {@code SITU_01} vs {@code PRUD_01}), et aucun
     * intitulé n'est partagé entre deux fichiers réglementaires.
     * <p>Le registre retenu est celui qui reconnaît le <em>plus</em> de feuilles : un classeur amputé
     * de quelques onglets reste identifiable.
     *
     * @throws IllegalArgumentException si aucune feuille connue n'est reconnue — c'est un fichier
     *         hors périmètre, donc une erreur de demande (400) et non une panne
     */
    public JobRegistry detecte(Collection<String> feuillesDuClasseur) {
        List<String> presentes = feuillesDuClasseur.stream()
                .filter(java.util.Objects::nonNull)
                .toList();

        JobRegistry meilleur = null;
        long meilleurScore = 0;
        for (JobRegistry registre : parCode.values()) {
            // on interroge le registre feuille par feuille plutôt que de comparer à sa liste
            // déclarée : c'est lui qui sait reconnaître les siennes, y compris quand leur intitulé
            // varie (l'onglet de BALG porte le millésime de l'exercice)
            long score = presentes.stream().filter(f -> registre.forSheet(f) != null).count();
            if (score > meilleurScore) {
                meilleurScore = score;
                meilleur = registre;
            }
        }
        if (meilleur == null) {
            throw new IllegalArgumentException(
                    "Aucun fichier réglementaire reconnu dans le classeur (feuilles : " + feuillesDuClasseur
                            + " ; fichiers pris en charge : " + codesFichiers() + ")");
        }
        return meilleur;
    }

    /** Codes des fichiers réglementaires pris en charge. */
    public Set<String> codesFichiers() {
        return parCode.values().stream()
                .map(JobRegistry::codeFichier)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String normalise(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
