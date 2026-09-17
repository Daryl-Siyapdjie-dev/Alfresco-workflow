package com.kimia.bcrg_integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture des <strong>contrôles de cohérence</strong> que SIRYF renvoie dans le corps d'une réponse.
 *
 * <p><strong>Pourquoi cette classe existe.</strong> SIRYF peut accepter une transmission — HTTP 200,
 * « Traitement effectué avec succès » — <em>et</em> signaler dans le même souffle que certaines
 * lignes ont été refusées par ses contrôles :
 *
 * <pre>{@code {"erreurs":[{"ligne":"4","erreur":" Code X est introuvable "}],
 *  "description":"Traitement effectué avec succès","statutCode":"OK"} }</pre>
 *
 * Sans lire ce tableau, un envoi « accepté avec douze lignes rejetées » passe pour un succès
 * complet, et le coût d'un tel faux succès est élevé : la période est consommée, la feuille est
 * réputée transmise, et personne n'est alerté des lignes perdues.
 *
 * <p><strong>Et un refus peut se cacher derrière un 200.</strong> SIRYF n'est pas régulier sur ce
 * point : le même refus arrive tantôt en {@code 409}, tantôt en {@code 200} au corps identique.
 *
 * <pre>{@code {"erreurs":[],
 *  "description":"Cette date d'arreté est deja utilisée pour cette institution FASeF-G.",
 *  "statutCode":"CONFLICT"} }</pre>
 *
 * Le tableau {@code erreurs} est vide : rien n'a été refusé <em>ligne à ligne</em>, c'est la remise
 * entière qui l'a été. Lu au seul {@code erreurs}, ce corps passait pour un succès complet — le
 * module annonçait la feuille transmise et <strong>gravait son idempotence</strong>, ce qui la
 * dispensait de tout rejeu ultérieur. D'où la lecture du {@code statutCode} : <em>toute valeur
 * autre que « OK » est un refus</em>, quel que soit le code HTTP qui l'accompagne.
 *
 * <p><strong>Robustesse avant tout.</strong> Aucune méthode ne lève : un corps illisible, un format
 * inattendu ou du texte brut donnent « aucune erreur signalée ». Transformer une transmission
 * réussie en échec parce qu'on n'a pas su lire la réponse serait pire que le mal.
 *
 * @param erreurs les lignes refusées, mises en forme pour être lues par un humain
 * @param refus   motif du refus de la remise entière, ou {@code null} si elle a été prise en compte
 */
public record ReponseSiryf(List<String> erreurs, String refus) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Au-delà, le résumé compte les erreurs restantes au lieu de les énumérer. */
    private static final int DETAIL_MAX = 5;

    /** Réponse ne signalant rien : ni refus de la remise, ni ligne rejetée. */
    private static final ReponseSiryf ACCEPTEE = new ReponseSiryf(List.of(), null);

    public ReponseSiryf {
        erreurs = List.copyOf(erreurs);
    }

    /**
     * Extrait les erreurs de ligne du corps de réponse, quelle qu'en soit la forme.
     * <p>SIRYF en emploie deux : un objet portant un tableau {@code erreurs}, et — sur les refus —
     * un tableau d'objets d'erreur à la racine. Tout le reste (texte brut, corps vide, JSON
     * inattendu) est traité comme « rien à signaler ».
     */
    public static ReponseSiryf lire(String corps) {
        if (corps == null || corps.isBlank()) {
            return ACCEPTEE;
        }
        try {
            JsonNode racine = JSON.readTree(corps);
            String refus = motifDeRefus(racine);
            JsonNode tableau = racine.isArray() ? racine : racine.path("erreurs");
            if (!tableau.isArray()) {
                return new ReponseSiryf(List.of(), refus);
            }
            List<String> erreurs = new ArrayList<>();
            for (JsonNode noeud : tableau) {
                String message = decrire(noeud);
                if (!message.isBlank()) {
                    erreurs.add(message);
                }
            }
            return new ReponseSiryf(erreurs, refus);
        } catch (Exception corpsNonJson) {
            return ACCEPTEE;                      // « Traitement effectué avec succès » en texte brut
        }
    }

    /**
     * Motif du refus de la remise, ou {@code null} si SIRYF l'a prise en compte.
     * <p>Le verdict tient dans {@code statutCode} : « OK » et rien d'autre vaut acceptation. Un corps
     * qui ne porte pas ce champ — texte brut, tableau d'erreurs nu — ne dit rien du sort de la
     * remise ; on ne lui fait pas dire un refus qu'il n'exprime pas.
     */
    private static String motifDeRefus(JsonNode racine) {
        String statut = racine.path("statutCode").asText("").trim();
        if (statut.isEmpty() || statut.equalsIgnoreCase("OK")) {
            return null;
        }
        String description = racine.path("description").asText("").trim().replaceAll("\\s+", " ");
        return description.isEmpty() ? "refus SIRYF (" + statut + ")" : description;
    }

    /** true si SIRYF n'a signalé aucune ligne en erreur. */
    public boolean sansErreur() {
        return erreurs.isEmpty();
    }

    /**
     * true si SIRYF a <strong>refusé la remise entière</strong> — à ne pas confondre avec des lignes
     * rejetées ({@link #sansErreur()}) : là, rien du tout n'a été enregistré.
     */
    public boolean refusee() {
        return refus != null;
    }

    /** Résumé lisible pour le compte rendu et la piste d'audit, borné en longueur. */
    public String resume() {
        if (erreurs.isEmpty()) {
            return "aucune erreur de cohérence";
        }
        StringBuilder texte = new StringBuilder()
                .append(erreurs.size()).append(erreurs.size() == 1 ? " ligne refusée" : " lignes refusées")
                .append(" par les contrôles BCRG : ");
        texte.append(String.join(" ; ", erreurs.subList(0, Math.min(DETAIL_MAX, erreurs.size()))));
        if (erreurs.size() > DETAIL_MAX) {
            texte.append(" … et ").append(erreurs.size() - DETAIL_MAX).append(" autre(s)");
        }
        return texte.toString();
    }

    /** Met en forme une erreur : « ligne 4 : Code X est introuvable ». */
    private static String decrire(JsonNode noeud) {
        if (noeud.isTextual()) {
            return noeud.asText().trim();
        }
        String message = noeud.path("erreur").asText("").trim();
        String ligne = noeud.path("ligne").asText("").trim();
        if (message.isEmpty()) {
            return "";
        }
        return ligne.isEmpty() ? message : "ligne " + ligne + " : " + message;
    }
}
