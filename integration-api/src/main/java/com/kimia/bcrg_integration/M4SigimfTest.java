package com.kimia.bcrg_integration;

import com.kimia.bcrg_integration.client.SigimfClient;
import com.kimia.bcrg_integration.client.dto.DataModelImfSigImfInformationGeneraleString;
import com.kimia.bcrg_integration.client.dto.SigImfInformationGenerale;
import com.kimia.bcrg_integration.client.dto.TransmissionImf;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Smoke test d'aller simple SIGIMF (m0infog) vers l'environnement BCRG.
 * <p>Actif uniquement sous le profil {@code smoketest} pour éviter tout envoi réel
 * au démarrage normal. Lancer avec :
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=smoketest}.
 * <p>Données issues du template SIGIMF réel (feuille M.0.INFO.G) — codes de la
 * nomenclature BCRG.
 */
//@Component
@Profile("smoketest")
public class M4SigimfTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(M4SigimfTest.class);
    private final SigimfClient sigimfClient;

    public M4SigimfTest(SigimfClient sigimfClient) { this.sigimfClient = sigimfClient; }

    @Override
    public void run(String... args) {
        // En-tête de transmission : dateArrete = "Date des rapports" du template (31/03/2019)
        TransmissionImf t = new TransmissionImf()
                .dateArrete(OffsetDateTime.parse("2019-03-31T00:00:00+00:00"))
                .statut(TransmissionImf.StatutEnum.CREATION);

        // Lignes d'informations générales avec les VRAIS codes de la feuille M.0.INFO.G
        List<SigImfInformationGenerale> lignes = List.of(
                new SigImfInformationGenerale().code("M.0.LS").information("Statut légal").donnee("SA"),
                new SigImfInformationGenerale().code("M.0.LD").information("Date d'agrément").donnee("01/05/23"),
                new SigImfInformationGenerale().code("M.0.MLO").information("Adresse du siège").donnee("Kipé"),
                new SigImfInformationGenerale().code("M.0.MLO.1").information("Ville").donnee("Conakry"),
                new SigImfInformationGenerale().code("M.0.MLO.2").information("Commune").donnee("Ratoma"),
                new SigImfInformationGenerale().code("M.0.MLO.3").information("Quartier").donnee("Kipé/Kaporo"),
                new SigImfInformationGenerale().code("M.0.TL").information("Numéro de téléphone").donnee("657 66 60 00"),
                new SigImfInformationGenerale().code("M.0.EM").information("Email").donnee("test@example.com"),
                new SigImfInformationGenerale().code("M.0.MD").information("Nom du Directeur Général").donnee("Test DG")
        );

        DataModelImfSigImfInformationGeneraleString payload =
                new DataModelImfSigImfInformationGeneraleString()
                        .transmission(t)
                        .items(lignes);

        try {
            var resp = sigimfClient.envoyerM0Infog(payload);
            log.info("→ Envoi de {} items : {}",
                payload.getItems() == null ? 0 : payload.getItems().size(),
                payload.getItems());
            log.info("SIGIMF m0infog — HTTP {} — corps : {}", resp.getStatusCode(), resp.getBody());
        } catch (BcrgApiException e) {
            // 4xx/5xx traduits par le socle : on affiche le diagnostic exploitable
            log.warn("SIGIMF m0infog — HTTP {} — message API : {} | description : {}",
                    e.getHttpStatus(), e.getApiMessage(), e.getApiDescription());
        } catch (Exception e) {
            log.error("Erreur SIGIMF m0infog : {}", e.getMessage(), e);
        }
    }
}