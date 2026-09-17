package com.kimia.bcrg_integration;

import com.kimia.bcrg_integration.client.SigimfClient;
import com.kimia.bcrg_integration.client.TransmissionClient;
import com.kimia.bcrg_integration.client.dto.*;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Profile("smoketest")
public class SequenceProbeTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SequenceProbeTest.class);

    private final RestClient http;               // client authentifié (pour le GET période)
    private final SigimfClient sigimf;
    private final TransmissionClient transmission;

    public SequenceProbeTest(RestClient bcrgAuthenticatedRestClient,
                             SigimfClient sigimf,
                             TransmissionClient transmission) {
        this.http = bcrgAuthenticatedRestClient;
        this.sigimf = sigimf;
        this.transmission = transmission;
    }

    @Override
    public void run(String... args) {
        
        final int exercice = 2026;
        final String mois = "AOUT";
        final OffsetDateTime dateArrete = OffsetDateTime.parse("2026-08-31T00:00:00+00:00");

        //log.info("▶ Cible : exercice={} mois={} dateArrete={}", exercice, mois, dateArrete);

        // Filtre commun pour verification / autorisation / soumission
        TransmissionModel tm = new TransmissionModel()
                .codeFichier(TransmissionModel.CodeFichierEnum.SIGIMF)
                .codeFeuille(null)
                .exercice(exercice)
                .annee(exercice)
                .periode(mois)
                .periodicite(TransmissionModel.PeriodiciteEnum.TRIMESTRIELLE)
                .dateArrete(dateArrete)
                .statut(TransmissionModel.StatutEnum.CREATION);

        // -- Étape 1 : la période existe-t-elle ? (LECTURE, sans effet de bord) --
        try {
            String r = http.get()
                    .uri(b -> b.path("/periodes/periode-exist")
                            .queryParam("exercice", exercice)
                            .queryParam("mois", mois).build())
                    .retrieve().body(String.class);
            log.info("① période {}/{} existe ? → {}", exercice, mois, r);
        } catch (Exception e) { log.warn("① période : {}", msg(e)); }

        // -- Étape 2 : import de la feuille m0infog --
        try {
            var resp = sigimf.envoyerM0Infog(buildInfoG(dateArrete));
            log.info("② import m0infog → HTTP {} — {}", resp.getStatusCode(), resp.getBody());
        } catch (Exception e) { log.warn("② import m0infog : {}", msg(e)); }

        // -- Étape 3 : vérification --
        try { log.info("③ vérification → {}", transmission.verification(tm)); }
        catch (Exception e) { log.warn("③ vérification : {}", msg(e)); }

        // -- Étape 4 : autorisation IMF --
        try { log.info("④ autorisation IMF → {}", transmission.autorisationImf(tm)); }
        catch (Exception e) { log.warn("④ autorisation IMF : {}", msg(e)); }

        // -- Étape 5 : soumission IMF (⚠️ transmission réelle sur l'env de test) --
        try { log.info("⑤ soumission IMF → {}", transmission.soumissionImf(tm)); }
        catch (Exception e) { log.warn("⑤ soumission IMF : {}", msg(e)); }
    }

    private DataModelImfSigImfInformationGeneraleString buildInfoG(OffsetDateTime dateArrete) {
        TransmissionImf t = new TransmissionImf()
                .dateArrete(dateArrete)
                .statut(TransmissionImf.StatutEnum.CREATION);
        List<SigImfInformationGenerale> lignes = List.of(
                new SigImfInformationGenerale().code("M.0.LS").information("Statut légal").donnee("SA"),
                new SigImfInformationGenerale().code("M.0.MD").information("Nom du DG").donnee("Test DG")
        );
        return new DataModelImfSigImfInformationGeneraleString().transmission(t).items(lignes);
    }

    private String msg(Exception e) {
        if (e instanceof BcrgApiException b)
            return "HTTP " + b.getHttpStatus() + " | " + b.getApiMessage() + " | " + b.getApiDescription();
        return e.getMessage();
    }
}