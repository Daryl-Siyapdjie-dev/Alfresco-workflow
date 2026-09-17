package com.kimia.bcrg_integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimia.bcrg_integration.auth.BearerTokenInterceptor;
import com.kimia.bcrg_integration.client.dto.MessageResponse;
import com.kimia.bcrg_integration.exception.BcrgApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fabrique de requêtes partagée par les deux clients : timeouts (connect/read) +
     * truststore optionnel via SSL bundle Spring. Corrige aussi l'absence de timeout
     * côté client d'auth.
     */
    private ClientHttpRequestFactory requestFactory(BcrgApiProperties props, SslBundles sslBundles) {
        HttpClient.Builder httpBuilder = HttpClient.newBuilder();
        if (props.connectTimeout() != null) {
            httpBuilder.connectTimeout(props.connectTimeout());
        }

        String bundleName = props.sslBundle();
        if (bundleName != null && !bundleName.isBlank()) {
            SslBundle bundle = sslBundles.getBundle(bundleName);
            httpBuilder.sslContext(bundle.createSslContext());
            log.info("Truststore BCRG : SSL bundle '{}' appliqué.", bundleName);
        } else {
            log.info("Truststore BCRG : truststore JVM par défaut (aucun SSL bundle configuré).");
        }

        var factory = new JdkClientHttpRequestFactory(httpBuilder.build());
        if (props.readTimeout() != null) {
            factory.setReadTimeout(props.readTimeout());
        }
        return factory;
    }

    /**
     * Gestionnaire d'erreur commun : traduit tout statut 4xx/5xx en {@link BcrgApiException},
     * en exploitant le corps {@link MessageResponse} si présent.
     */
    private RestClient.Builder withErrorHandling(RestClient.Builder builder) {
        return builder.defaultStatusHandler(
                httpStatus -> httpStatus.isError(),
                (request, response) -> {
                    String rawBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                    MessageResponse parsed = null;
                    if (rawBody != null && !rawBody.isBlank()) {
                        try {
                            parsed = objectMapper.readValue(rawBody, MessageResponse.class);
                        } catch (Exception ignored) {
                            // corps non conforme au modèle MessageResponse : on conserve le brut
                        }
                    }
                    throw new BcrgApiException(response.getStatusCode(), parsed, rawBody);
                });
    }

    /** Client non authentifié (auth : signin / refresh). */
    @Bean
    public RestClient bcrgRestClient(BcrgApiProperties props, SslBundles sslBundles) {
        return withErrorHandling(RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props, sslBundles)))
                .build();
    }

    /** Client authentifié (Bearer injecté + re-essai 401/403) pour tous les autres appels. */
    @Bean
    public RestClient bcrgAuthenticatedRestClient(BcrgApiProperties props,
                                                  SslBundles sslBundles,
                                                  BearerTokenInterceptor bearerInterceptor) {
        return withErrorHandling(RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props, sslBundles))
                .requestInterceptor(bearerInterceptor))
                .build();
    }
}
