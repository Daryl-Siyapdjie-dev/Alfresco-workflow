package com.kimia.bcrg_integration.auth;

import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class TokenStore {

    private String accessToken;
    private String refreshToken;
    private Instant expiresAt = Instant.EPOCH;

    public synchronized void update(String accessToken, String refreshToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    /**
     * Invalide le token courant pour forcer un renouvellement au prochain appel à
     * {@code getValidToken()} (utilisé quand le serveur rejette un token pourtant
     * localement « valide » — 401/403).
     */
    public synchronized void invalidate() {
        this.accessToken = null;
        this.expiresAt = Instant.EPOCH;
    }

    public synchronized String accessToken()  { return accessToken; }
    public synchronized String refreshToken()  { return refreshToken; }

    public synchronized boolean isExpired(long marginSeconds) {
        return accessToken == null
            || Instant.now().plusSeconds(marginSeconds).isAfter(expiresAt);
    }
}