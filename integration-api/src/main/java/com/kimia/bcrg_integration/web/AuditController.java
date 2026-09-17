package com.kimia.bcrg_integration.web;

import com.kimia.bcrg_integration.audit.Acknowledgement;
import com.kimia.bcrg_integration.audit.AcknowledgementStore;
import com.kimia.bcrg_integration.audit.AuditEvent;
import com.kimia.bcrg_integration.audit.AuditTarget;
import com.kimia.bcrg_integration.audit.AuditTrail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Predicate;

/**
 * Relecture de la piste d'audit et des accusés de réception (Lot 7, tickets 7.1 et 7.3 :
 * « archivés et <strong>retrouvables</strong> »).
 * <p>En lecture seule, sous {@code /api/internal/} donc soumis au même contrôle d'accès que le
 * déclenchement ({@link InternalApiFilter}) : la piste d'audit est une donnée d'exploitation.
 */
@RestController
@RequestMapping("/api/internal/audit")
public class AuditController {

    private final AuditTrail trail;
    private final AcknowledgementStore acknowledgements;

    public AuditController(AuditTrail trail, AcknowledgementStore acknowledgements) {
        this.trail = trail;
        this.acknowledgements = acknowledgements;
    }

    /**
     * Les derniers événements d'audit, du plus ancien au plus récent.
     *
     * @param limit       nombre maximal d'événements rendus (0 ou négatif = tous)
     * @param codeFichier filtre optionnel sur le fichier réglementaire
     * @param feuille     filtre optionnel sur la feuille
     */
    @GetMapping("/events")
    public List<AuditEvent> events(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(required = false) String codeFichier,
                                   @RequestParam(required = false) String feuille) {
        Predicate<AuditTarget> filtre = filtre(codeFichier, feuille);
        List<AuditEvent> filtres = trail.events().stream()
                .filter(e -> filtre.test(e.cible()))
                .toList();
        if (limit <= 0 || limit >= filtres.size()) {
            return filtres;
        }
        return List.copyOf(filtres.subList(filtres.size() - limit, filtres.size()));
    }

    /** Les accusés de réception archivés, filtrables par fichier et/ou feuille. */
    @GetMapping("/accuses")
    public List<Acknowledgement> accuses(@RequestParam(required = false) String codeFichier,
                                         @RequestParam(required = false) String feuille) {
        Predicate<AuditTarget> filtre = filtre(codeFichier, feuille);
        return acknowledgements.all().stream()
                .filter(a -> filtre.test(a.cible()))
                .toList();
    }

    /** Filtre sur la cible : un critère absent ne filtre rien. */
    private static Predicate<AuditTarget> filtre(String codeFichier, String feuille) {
        return cible -> {
            if (cible == null) {
                return codeFichier == null && feuille == null;
            }
            if (codeFichier != null && !codeFichier.equals(cible.codeFichier())) {
                return false;
            }
            return feuille == null || feuille.equals(cible.feuille());
        };
    }
}
