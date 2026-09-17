package com.kimia.bcrg_integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

//@Component
@Profile("smoketest")
public class PeriodeProbeTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PeriodeProbeTest.class);
    private final RestClient http;

    public PeriodeProbeTest(RestClient bcrgAuthenticatedRestClient) {
        this.http = bcrgAuthenticatedRestClient;
    }

    @Override
    public void run(String... args) {
        String[] mois = {"JANVIER","FEVRIER","MARS","AVRIL","MAI","JUIN",
                         "JUILLET","AOUT","SEPTEMBRE","OCTOBRE","NOVEMBRE","DECEMBRE"};
        log.info("=== Balayage des périodes existantes (lecture seule) ===");
        for (int ex : new int[]{2023, 2024, 2025, 2026}) {
            for (String m : mois) {
                final int exercice = ex; final String mois1 = m;
                try {
                    String r = http.get()
                            .uri(b -> b.path("/periodes/periode-exist")
                                    .queryParam("exercice", exercice)
                                    .queryParam("mois", mois1).build())
                            .retrieve().body(String.class);
                    if (r != null && !r.isBlank() && !"false".equalsIgnoreCase(r) && !"null".equalsIgnoreCase(r)) {
                        log.info("✅ EXISTE : {} {} → {}", exercice, mois1, r);
                    }
                } catch (Exception e) {
                    log.warn("{} {} : {}", exercice, mois1, e.getMessage());
                }
            }
        }
        log.info("=== Fin du balayage ===");
    }
}