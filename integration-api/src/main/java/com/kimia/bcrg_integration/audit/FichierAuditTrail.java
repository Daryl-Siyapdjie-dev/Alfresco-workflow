package com.kimia.bcrg_integration.audit;

import com.kimia.bcrg_integration.audit.AuditEvent.Resultat;
import com.kimia.bcrg_integration.persistance.JournalFichier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Piste d'audit <strong>conservée sur disque</strong> (§9) : une ligne par événement.
 *
 * <p><strong>Ce que la version en mémoire ne pouvait pas tenir.</strong> La traçabilité
 * réglementaire demande de reconstituer, après coup, pourquoi une feuille est partie, n'est pas
 * partie, ou a été rejouée. Un journal qui s'efface à chaque redémarrage ne répond pas à cette
 * demande — il répond à « qu'a fait le module depuis qu'il tourne ? », qui n'est pas la même
 * question.
 *
 * <p><strong>Écriture immédiate, lecture en mémoire.</strong> Chaque événement part sur disque au
 * moment où il se produit — c'est ce qui compte pour l'audit — et une fenêtre des plus récents reste
 * en mémoire pour l'API de consultation, sans relire le fichier à chaque appel. Au démarrage, cette
 * fenêtre est repeuplée depuis le disque : l'historique traverse les redémarrages.
 *
 * <p><strong>Un format lisible plutôt qu'un format savant.</strong> Champs séparés par une
 * tabulation, dans un ordre fixe. Une piste d'audit finit par être lue par un humain qui cherche à
 * comprendre un envoi : elle doit s'ouvrir dans n'importe quel éditeur et se lire sans outil. Les
 * tabulations et retours à la ligne du détail sont remplacés par des espaces, pour qu'une ligne
 * reste une ligne.
 *
 * <p>Le détail a déjà traversé {@link AuditRedactor} avant d'arriver ici : aucun jeton ni mot de
 * passe ne peut se retrouver dans le fichier (ticket 7.4).
 */
public class FichierAuditTrail implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(FichierAuditTrail.class);

    private static final String SEP = "\t";
    private static final int NB_CHAMPS = 10;

    private final JournalFichier journal;
    private final int maxEvents;
    private final Deque<AuditEvent> recents = new ArrayDeque<>();

    public FichierAuditTrail(Path fichier, int maxEvents) {
        this.journal = new JournalFichier(fichier);
        this.maxEvents = maxEvents;
        recharger();
        log.info("Piste d'audit {} : {} événement(s) rechargé(s)", journal.fichier(), recents.size());
    }

    @Override
    public synchronized void append(AuditEvent evenement) {
        journal.ajouter(ecrire(evenement));
        recents.addLast(evenement);
        while (recents.size() > maxEvents) {
            recents.removeFirst();
        }
    }

    @Override
    public synchronized List<AuditEvent> events() {
        return List.copyOf(recents);
    }

    private static String ecrire(AuditEvent e) {
        AuditTarget cible = e.cible();
        return String.join(SEP,
                texte(e.horodatage()),
                texte(e.acteur()),
                texte(e.action()),
                texte(cible == null ? null : cible.codeFichier()),
                texte(cible == null ? null : cible.feuille()),
                texte(cible == null ? null : cible.dateArrete()),
                texte(e.resultat()),
                texte(e.httpStatus()),
                Long.toString(e.dureeMillis()),
                surUneLigne(e.detail()));
    }

    /**
     * Relit une ligne. Une ligne abîmée est <strong>ignorée</strong> : un fichier partiellement
     * corrompu — coupure d'alimentation en pleine écriture, édition à la main — ne doit pas empêcher
     * le module de démarrer, ni faire perdre les milliers de lignes saines qui l'entourent.
     */
    private static AuditEvent relire(String ligne) {
        String[] c = ligne.split(SEP, -1);
        if (c.length != NB_CHAMPS) {
            return null;
        }
        try {
            AuditTarget cible = (c[3].isEmpty() && c[4].isEmpty() && c[5].isEmpty()) ? null
                    : new AuditTarget(vide(c[3]), vide(c[4]),
                            c[5].isEmpty() ? null : OffsetDateTime.parse(c[5]));
            return new AuditEvent(Instant.parse(c[0]), vide(c[1]), AuditAction.valueOf(c[2]), cible,
                    Resultat.valueOf(c[6]), c[7].isEmpty() ? null : Integer.valueOf(c[7]),
                    vide(c[9]), Long.parseLong(c[8]));
        } catch (RuntimeException ligneAbimee) {
            return null;
        }
    }

    private void recharger() {
        List<String> lignes = journal.lignes();
        int depuis = Math.max(0, lignes.size() - maxEvents);
        int ignorees = 0;
        for (String ligne : lignes.subList(depuis, lignes.size())) {
            AuditEvent evenement = relire(ligne);
            if (evenement == null) {
                ignorees++;
            } else {
                recents.addLast(evenement);
            }
        }
        if (ignorees > 0) {
            log.warn("Piste d'audit : {} ligne(s) illisible(s) ignorée(s) au rechargement", ignorees);
        }
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    private static String vide(String valeur) {
        return valeur.isEmpty() ? null : valeur;
    }

    private static String surUneLigne(String detail) {
        return detail == null ? "" : detail.replaceAll("[\t\r\n]+", " ");
    }
}
