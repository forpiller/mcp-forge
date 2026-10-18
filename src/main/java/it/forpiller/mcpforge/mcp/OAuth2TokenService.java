package it.forpiller.mcpforge.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fa login (grant_type=password) per conto di una fonte configurata con
 * {@link OAuth2PasswordGrantConfig} e mette in cache l'access token finché
 * resta valido, rinnovandolo automaticamente poco prima della scadenza.
 * Una fonte alla volta fa al massimo una richiesta di login concorrente.
 */
public class OAuth2TokenService {

    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 60;
    private static final long DEFAULT_TTL_SECONDS = 300;

    private final RestClient restClient;
    private final ConcurrentHashMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public OAuth2TokenService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getAccessToken(String sourceName, OAuth2PasswordGrantConfig config) {
        CachedToken cached = cache.get(sourceName);
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }

        ReentrantLock lock = locks.computeIfAbsent(sourceName, n -> new ReentrantLock());
        lock.lock();
        try {
            cached = cache.get(sourceName);
            if (cached != null && cached.isValid()) {
                return cached.accessToken();
            }
            CachedToken fresh = login(config);
            cache.put(sourceName, fresh);
            return fresh.accessToken();
        } finally {
            lock.unlock();
        }
    }

    /** Scarta il token in cache — usato dopo un 401, per forzare un nuovo login al prossimo tentativo. */
    public void invalidate(String sourceName) {
        cache.remove(sourceName);
    }

    private CachedToken login(OAuth2PasswordGrantConfig config) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("username", config.username());
        form.add("password", config.password());

        TokenResponse response = restClient.post()
                .uri(config.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Login OAuth2 fallito: nessun access_token da " + config.tokenUrl());
        }

        long ttl = response.expiresIn() != null ? response.expiresIn() : DEFAULT_TTL_SECONDS;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(0, ttl - EXPIRY_SAFETY_MARGIN_SECONDS));
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }
}
