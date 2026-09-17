package com.kimia.bcrg_integration.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournit les beans de rejeu à partir des propriétés {@code bcrg.retry.*} (valeurs par défaut sûres),
 * et le {@link Sleeper} de production ({@code Thread::sleep}).
 */
@Configuration
public class RetryConfig {

    @Bean
    public RetryPolicy retryPolicy(
            @Value("${bcrg.retry.max-attempts:4}") int maxAttempts,
            @Value("${bcrg.retry.initial-backoff-millis:500}") long initialBackoffMillis,
            @Value("${bcrg.retry.multiplier:2.0}") double multiplier,
            @Value("${bcrg.retry.max-backoff-millis:8000}") long maxBackoffMillis) {
        return new RetryPolicy(maxAttempts, initialBackoffMillis, multiplier, maxBackoffMillis);
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }
}
