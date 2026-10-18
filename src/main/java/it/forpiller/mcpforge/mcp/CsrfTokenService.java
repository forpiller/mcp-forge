package it.forpiller.mcpforge.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recupera e mette in cache il token CSRF (XSRF-TOKEN) per le chiamate a fonti
 * che usano CookieCsrfTokenRepository di Spring Security — necessario per
 * tutti i metodi state-changing (POST, PUT, DELETE, PATCH).
 *
 * Strategia:
 * 1. GET su URL noti del baseUrl per ottenere il cookie XSRF-TOKEN.
 * 2. Se non arriva dal GET, POST "preflight" a vuoto: Spring Security scrive
 *    il cookie nel 403 di risposta tramite il deferred-token pattern.
 */
public class CsrfTokenService {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenService.class);

    private final RestClient restClient;
    private final ConcurrentHashMap<String, CsrfEntry> cache = new ConcurrentHashMap<>();

    public CsrfTokenService(RestClient restClient) {
        this.restClient = restClient;
    }

    public record CsrfEntry(String token, String cookieHeader) {}

    public CsrfEntry getOrFetch(String sourceName, String baseUrl) {
        CsrfEntry cached = cache.get(sourceName);
        if (cached != null) return cached;

        CsrfEntry fetched = fetchFromGet(baseUrl);
        if (fetched == null) fetched = fetchFromPreflight(baseUrl);
        if (fetched != null) {
            cache.put(sourceName, fetched);
            log.debug("CSRF token ottenuto per '{}': {}", sourceName, fetched.token());
        } else {
            log.debug("CSRF token non trovato per '{}', la chiamata potrebbe ottenere 403", sourceName);
        }
        return fetched;
    }

    public void invalidate(String sourceName) {
        cache.remove(sourceName);
        log.debug("CSRF cache invalidata per '{}'", sourceName);
    }

    private CsrfEntry fetchFromGet(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        for (String url : new String[]{base + "/actuator/health", base + "/", base}) {
            try {
                CsrfEntry entry = restClient.get().uri(url).exchange((req, res) -> {
                    List<String> cookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                    return extractFromCookies(cookies);
                });
                if (entry != null) {
                    log.debug("CSRF token trovato via GET {}", url);
                    return entry;
                }
            } catch (Exception e) {
                log.debug("CSRF GET {} fallita: {}", url, e.getMessage());
            }
        }
        return null;
    }

    private CsrfEntry fetchFromPreflight(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        try {
            return restClient.post()
                    .uri(base + "/api/__csrf_probe__")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .exchange((req, res) -> {
                        log.debug("CSRF preflight POST {} → {}", base, res.getStatusCode());
                        List<String> cookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                        return extractFromCookies(cookies);
                    });
        } catch (Exception e) {
            log.debug("CSRF preflight POST fallita per {}: {}", base, e.getMessage());
            return null;
        }
    }

    private CsrfEntry extractFromCookies(List<String> setCookies) {
        if (setCookies == null) return null;
        String csrfToken = null;
        List<String> pairs = new ArrayList<>();
        for (String header : setCookies) {
            String pair = header.split(";")[0];
            pairs.add(pair);
            if (pair.startsWith("XSRF-TOKEN=")) {
                csrfToken = pair.substring("XSRF-TOKEN=".length());
            }
        }
        return csrfToken != null ? new CsrfEntry(csrfToken, String.join("; ", pairs)) : null;
    }
}
