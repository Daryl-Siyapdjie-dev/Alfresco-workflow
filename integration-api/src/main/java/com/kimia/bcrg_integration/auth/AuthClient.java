package com.kimia.bcrg_integration.auth;

import com.kimia.bcrg_integration.client.dto.LoginRequest;
import com.kimia.bcrg_integration.client.dto.LoginResponse;
import com.kimia.bcrg_integration.client.dto.RefreshTokenRequest;
import com.kimia.bcrg_integration.client.dto.RefreshTokenResponse;
import com.kimia.bcrg_integration.config.BcrgApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;

@Component
public class AuthClient {

    private final RestClient client;
    private final TokenStore tokenStore;
    private final BcrgApiProperties props;

    public AuthClient(RestClient bcrgRestClient, TokenStore tokenStore, BcrgApiProperties props) {
        this.client = bcrgRestClient;
        this.tokenStore = tokenStore;
        this.props = props;
    }

    /** Renvoie toujours un token valide : réutilise, rafraîchit, ou se reconnecte. */
    public synchronized String getValidToken() {
        if (!tokenStore.isExpired(props.tokenRefreshMarginSeconds())) {
            return tokenStore.accessToken();
        }
        if (tokenStore.refreshToken() != null) {
            try { refresh(); return tokenStore.accessToken(); }
            catch (Exception e) { /* refresh échoué → on retente un signin complet */ }
        }
        signin();
        return tokenStore.accessToken();
    }

    public void signin() {
        var body = new LoginRequest().username(props.username()).password(props.password());
        LoginResponse resp = client.post()
                .uri("/auth/signin")
                .body(body)
                .retrieve()
                .body(LoginResponse.class);
        store(resp.getToken(), resp.getRefreshToken(), resp.getExpiration());
    }

    public void refresh() {
        var body = new RefreshTokenRequest().refreshToken(tokenStore.refreshToken());
        RefreshTokenResponse resp = client.post()
                .uri("/auth/refresh")
                .body(body)
                .retrieve()
                .body(RefreshTokenResponse.class);
        store(resp.getAccessToken(), resp.getRefreshToken(), resp.getExpiration());
    }

    private void store(String access, String refresh, Long expiration) {
        Instant expiresAt = (expiration != null)
                ? toInstant(expiration)
                : Instant.now().plusSeconds(300);
        tokenStore.update(access, refresh, expiresAt);
    }

    /**
     * Convertit l'epoch renvoyé par l'API en {@link Instant}, en détectant l'unité :
     * l'OpenAPI documente des millisecondes, mais on reste robuste au cas où le serveur
     * renverrait des secondes (à confirmer sur token réel, cf. §12 du CDC).
     * <p>Seuil : une valeur en secondes de l'ère courante fait ~10 chiffres (~1,7e9),
     * en millisecondes ~13 chiffres (~1,7e12). Sous 1e12 → secondes, sinon → millisecondes.
     */
    static Instant toInstant(long epoch) {
        return (epoch < 1_000_000_000_000L)
                ? Instant.ofEpochSecond(epoch)
                : Instant.ofEpochMilli(epoch);
    }
}