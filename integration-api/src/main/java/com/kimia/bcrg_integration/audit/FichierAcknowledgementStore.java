package com.kimia.bcrg_integration.audit;

import com.kimia.bcrg_integration.persistance.JournalFichier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Archive des accusés de réception <strong>conservée sur disque</strong> (ticket 7.3).
 *
 * <p>Un accusé est la <em>pièce justificative</em> d'un envoi : il prouve quoi a été transmis, quand,
 * et ce que la BCRG a répondu. C'est précisément ce qu'on veut pouvoir produire des mois plus tard,
 * et donc ce qui ne peut pas vivre en mémoire.
 *
 * <p>Même format que la piste d'audit : une ligne par accusé, champs séparés par une tabulation,
 * lisible sans outil. Le corps de la réponse a déjà été masqué de ses secrets avant d'arriver ici —
 * il contient notamment les <strong>erreurs de cohérence</strong> renvoyées par SIRYF, qui n'ont
 * de valeur que si on les conserve.
 */
public class FichierAcknowledgementStore implements AcknowledgementStore {

    private static final Logger log = LoggerFactory.getLogger(FichierAcknowledgementStore.class);

    private static final String SEP = "\t";
    private static final int NB_CHAMPS = 6;

    private final JournalFichier journal;
    private final Map<AuditTarget, Acknowledgement> accuses = new LinkedHashMap<>();

    public FichierAcknowledgementStore(Path fichier) {
        this.journal = new JournalFichier(fichier);
        int ignorees = 0;
        for (String ligne : journal.lignes()) {
            Acknowledgement accuse = relire(ligne);
            if (accuse == null) {
                ignorees++;
            } else {
                deposer(accuse);
            }
        }
        log.info("Accusés {} : {} archivé(s) rechargé(s){}", journal.fichier(), accuses.size(),
                ignorees > 0 ? " (" + ignorees + " ligne(s) illisible(s) ignorée(s))" : "");
    }

    @Override
    public synchronized void store(Acknowledgement accuse) {
        journal.ajouter(ecrire(accuse));
        deposer(accuse);
    }

    @Override
    public synchronized Optional<Acknowledgement> find(AuditTarget cible) {
        return Optional.ofNullable(accuses.get(cible));
    }

    @Override
    public synchronized List<Acknowledgement> all() {
        return List.copyOf(new ArrayList<>(accuses.values()));
    }

    /** Le plus récent l'emporte et passe en fin : le journal garde toute l'histoire, la vue le dernier état. */
    private void deposer(Acknowledgement accuse) {
        accuses.remove(accuse.cible());
        accuses.put(accuse.cible(), accuse);
    }

    private static String ecrire(Acknowledgement a) {
        AuditTarget cible = a.cible();
        return String.join(SEP,
                a.horodatage().toString(),
                texte(cible == null ? null : cible.codeFichier()),
                texte(cible == null ? null : cible.feuille()),
                texte(cible == null ? null : cible.dateArrete()),
                Integer.toString(a.httpStatus()),
                surUneLigne(a.corps()));
    }

    private static Acknowledgement relire(String ligne) {
        String[] c = ligne.split(SEP, -1);
        if (c.length != NB_CHAMPS) {
            return null;
        }
        try {
            AuditTarget cible = new AuditTarget(vide(c[1]), vide(c[2]),
                    c[3].isEmpty() ? null : OffsetDateTime.parse(c[3]));
            return new Acknowledgement(cible, Instant.parse(c[0]), Integer.parseInt(c[4]), vide(c[5]));
        } catch (RuntimeException ligneAbimee) {
            return null;
        }
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    private static String vide(String valeur) {
        return valeur.isEmpty() ? null : valeur;
    }

    private static String surUneLigne(String corps) {
        return corps == null ? "" : corps.replaceAll("[\t\r\n]+", " ");
    }
}
