package com.kimia.bcrg_integration.pipeline;

import com.kimia.bcrg_integration.pipeline.IntegrationResult.Outcome;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compte rendu du traitement d'un <strong>dossier</strong> (un classeur, N feuilles) : c'est ce que
 * le module rend à l'appelant — runner de smoke test ou endpoint interne (Lot 8).
 * <p>Le {@code bilan} pré-agrège les issues par classe pour que l'appelant sache d'un coup d'œil si
 * le dossier est passé, sans reparcourir la liste (et pour que ce soit lisible dans la réponse JSON).
 *
 * @param fichier     nom du classeur traité
 * @param codeFichier code du fichier réglementaire (ex. « SIGIMF »)
 * @param dateArrete  période de la transmission
 * @param bilan       nombre de feuilles par issue, dans l'ordre de l'énumération
 * @param resultats   issue détaillée, feuille par feuille, dans l'ordre de traitement
 */
public record DossierReport(String fichier, String codeFichier, OffsetDateTime dateArrete,
                            Map<String, Integer> bilan, List<IntegrationResult> resultats) {

    public DossierReport {
        bilan = Map.copyOf(bilan);
        resultats = List.copyOf(resultats);
    }

    /** Construit le compte rendu et son bilan à partir des issues collectées. */
    public static DossierReport of(String fichier, String codeFichier, OffsetDateTime dateArrete,
                                   List<IntegrationResult> resultats) {
        Map<String, Integer> bilan = new LinkedHashMap<>();
        for (Outcome outcome : Outcome.values()) {
            long n = resultats.stream().filter(r -> r.outcome() == outcome).count();
            if (n > 0) {
                bilan.put(outcome.name(), (int) n);
            }
        }
        return new DossierReport(fichier, codeFichier, dateArrete, bilan, resultats);
    }

    public int total() {
        return resultats.size();
    }

    public int count(Outcome outcome) {
        return bilan.getOrDefault(outcome.name(), 0);
    }

    /**
     * true si aucune feuille ne demande une intervention — <strong>y compris une feuille acceptée
     * avec des lignes refusées</strong> : la donnée est partie amputée, le dossier n'est pas en règle
     * pour autant. Une feuille déjà transmise (idempotence) ou
     * <strong>hors période</strong> n'en est pas une : dans les deux cas le module a bien fait son
     * travail, il a simplement décidé de ne pas envoyer.
     */
    public boolean isComplet() {
        return resultats.stream().noneMatch(r -> r.outcome() == Outcome.ERREUR_DONNEES
                || r.outcome() == Outcome.ERREUR_API
                || r.outcome() == Outcome.ERREUR_TECHNIQUE
                || r.outcome() == Outcome.ACCEPTE_AVEC_ERREURS);
    }
}
