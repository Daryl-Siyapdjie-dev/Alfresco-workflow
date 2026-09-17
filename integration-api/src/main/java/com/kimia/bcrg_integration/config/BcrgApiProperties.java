package com.kimia.bcrg_integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "bcrg.api")
public record BcrgApiProperties(
        String baseUrl,
        String username,
        String password,
        Duration connectTimeout,
        Duration readTimeout,
        long tokenRefreshMarginSeconds,
        /**
         * Nom du SSL bundle Spring (cf. {@code spring.ssl.bundle.jks.<nom>}) à appliquer
         * au truststore des appels BCRG. Optionnel : laisser vide/null pour utiliser le
         * truststore JVM par défaut (CA publique). À renseigner quand le certificat serveur
         * (CA privée du port :8186) sera fourni.
         */
        String sslBundle
) {}