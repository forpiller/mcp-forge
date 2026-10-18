package it.forpiller.mcpforge.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fa login con POST JSON {username, password} per fonti configurate con
 * {@link JsonLoginConfig}. Mette in cache il token, lo rinnova automaticamente
 * poco prima della scadenza. Alternativa a {@link OAuth2TokenService} per API
 * che non espongono un endpoint OAuth2 standard ma un proprio endpoint di login.
 */
public class JsonLoginTokenService {

    private static final Logger log = LoggerFactory.getLogger(JsonLoginTokenService.class);
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 60;
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public JsonLoginTokenService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getAccessToken(String sourceName, JsonLoginConfig config) {
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

    public void invalidate(String sourceName) {
        cache.remove(sourceName);
    }

    private CachedToken login(JsonLoginConfig config) {
        Map<String, String> body = new HashMap<>();
        body.put("username", config.username());
        body.put("password", config.password());

        String baseUrl = config.loginUrl().replaceAll("/api/login$", "").replaceAll("/login$", "");

        // 1. Prova GET su URL noti per ottenere il cookie XSRF-TOKEN
        String csrfToken = fetchCsrfToken(config.loginUrl());
        String cookieHeader = csrfToken != null ? "XSRF-TOKEN=" + csrfToken : null;

        // 2. Fallback: Spring Security scrive XSRF-TOKEN nel Set-Cookie del 403,
        //    perché carica il deferred token prima di rifiutare. Raccogliamo anche
        //    eventuali session cookie (JSESSIONID) nella stessa risposta.
        if (csrfToken == null) {
            PreflightResult preflight = fetchPreflightResult(config.loginUrl(), body);
            if (preflight != null) {
                csrfToken = preflight.csrfToken();
                cookieHeader = preflight.cookieHeader();
                log.debug("JSON login CSRF token ottenuto dal preflight POST, cookie: {}", cookieHeader);
            }
        }

        final String finalCsrfToken = csrfToken;
        final String finalCookieHeader = cookieHeader;

        // Usiamo exchange() per leggere Set-Cookie nella risposta (jdro-uaa restituisce il token
        // come cookie "access_token", non come campo JSON nel body).
        return restClient.post()
                .uri(config.loginUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Origin", baseUrl)
                .header("Referer", baseUrl + "/ui/auth")
                .headers(h -> {
                    if (finalCsrfToken != null) {
                        h.add("X-XSRF-TOKEN", finalCsrfToken);
                        h.add(HttpHeaders.COOKIE, finalCookieHeader);
                    }
                })
                .body(body)
                .exchange((req, res) -> {
                    if (!res.getStatusCode().is2xxSuccessful()) {
                        String errBody;
                        try { errBody = new String(res.getBody().readAllBytes()); }
                        catch (Exception ignored) { errBody = res.getStatusCode().toString(); }
                        throw new IllegalStateException(
                                "Login JSON fallito: " + res.getStatusCode() + " da " + config.loginUrl() + " — " + errBody);
                    }

                    // 1. Prova a leggere il token dai cookie di risposta (pattern jdro-uaa)
                    List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                    log.debug("JSON login risposta Set-Cookie={}", setCookies);
                    if (setCookies != null) {
                        for (String c : setCookies) {
                            String pair = c.split(";")[0];
                            if (pair.startsWith("access_token=")) {
                                String token = pair.substring("access_token=".length());
                                if (!token.isBlank()) {
                                    long ttl = extractTtlFromJwt(token);
                                    if (ttl <= 0) ttl = DEFAULT_TTL_SECONDS;
                                    log.debug("JSON login: token letto dal cookie 'access_token'");
                                    return new CachedToken(token,
                                            Instant.now().plusSeconds(Math.max(0, ttl - EXPIRY_SAFETY_MARGIN_SECONDS)));
                                }
                            }
                        }
                    }

                    // 2. Fallback: leggi dal body JSON
                    String responseBody = new String(res.getBody().readAllBytes());
                    String token = extractToken(responseBody, config.effectiveTokenPath());
                    if (token == null || token.isBlank()) {
                        throw new IllegalStateException(
                                "Login JSON fallito: token non trovato né nei cookie né nel campo '"
                                        + config.effectiveTokenPath() + "' da " + config.loginUrl()
                                        + " — body: " + responseBody);
                    }
                    long ttl = extractTtlFromBody(responseBody);
                    if (ttl <= 0) ttl = extractTtlFromJwt(token);
                    if (ttl <= 0) ttl = DEFAULT_TTL_SECONDS;
                    return new CachedToken(token,
                            Instant.now().plusSeconds(Math.max(0, ttl - EXPIRY_SAFETY_MARGIN_SECONDS)));
                });
    }

    private String fetchCsrfToken(String loginUrl) {
        String baseUrl = loginUrl.replaceAll("/api/login$", "").replaceAll("/login$", "");
        // In jdro-app il frontend è su /ui/auth — Spring Security setta il cookie XSRF lì.
        // La root è spesso nginx/CDN e non setta il cookie.
        for (String url : new String[]{ baseUrl + "/ui/auth", baseUrl + "/ui/", loginUrl, baseUrl }) {
            try {
                String token = fetchCsrfFromUrl(url);
                if (token != null) {
                    log.debug("JSON login CSRF token trovato via GET {}: {}", url, token);
                    return token;
                }
            } catch (Exception e) {
                log.debug("JSON login CSRF fetch fallita per {}: {}", url, e.getMessage());
            }
        }
        log.debug("JSON login CSRF: nessun token trovato, procedo senza CSRF");
        return null;
    }

    /**
     * POST senza CSRF: Spring Security scrive XSRF-TOKEN nel Set-Cookie del 403 (deferred token).
     * Raccogliamo anche tutti gli altri cookie (es. session) per inviarli nel POST reale.
     */
    private PreflightResult fetchPreflightResult(String loginUrl, Map<String, String> body) {
        try {
            return restClient.post()
                    .uri(loginUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, res) -> {
                        log.debug("JSON login preflight POST {} → status={}", loginUrl, res.getStatusCode());
                        List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                        log.debug("JSON login preflight POST {} → Set-Cookie={}", loginUrl, setCookies);
                        if (setCookies == null) return null;
                        String csrfToken = null;
                        List<String> pairs = new ArrayList<>();
                        for (String header : setCookies) {
                            String pair = header.split(";")[0]; // "NAME=VALUE"
                            pairs.add(pair);
                            if (pair.startsWith("XSRF-TOKEN=")) {
                                csrfToken = pair.substring("XSRF-TOKEN=".length());
                            }
                        }
                        if (csrfToken == null) return null;
                        return new PreflightResult(csrfToken, String.join("; ", pairs));
                    });
        } catch (Exception e) {
            log.debug("JSON login preflight POST fallito: {}", e.getMessage());
            return null;
        }
    }

    private record PreflightResult(String csrfToken, String cookieHeader) {}

    private String fetchCsrfFromUrl(String url) {
        return restClient.get()
                .uri(url)
                .exchange((req, res) -> {
                    log.debug("JSON login CSRF GET {} → status={}", url, res.getStatusCode());
                    List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                    log.debug("JSON login CSRF GET {} → Set-Cookie={}", url, setCookies);
                    if (setCookies == null) return null;
                    for (String cookie : setCookies) {
                        if (cookie.startsWith("XSRF-TOKEN=")) {
                            return cookie.split(";")[0].substring("XSRF-TOKEN=".length());
                        }
                    }
                    return null;
                });
    }

    private String extractToken(String responseBody, String path) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 1. Prova il campo configurato (dot-notation)
            String[] parts = path.split("\\.");
            JsonNode node = root;
            for (String part : parts) {
                node = node.path(part);
                if (node.isMissingNode()) { node = null; break; }
            }
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }

            // 2. Prova nomi comuni
            for (String candidate : new String[]{"token", "accessToken", "access_token", "jwt", "id_token"}) {
                JsonNode n = root.path(candidate);
                if (!n.isMissingNode() && n.isTextual() && !n.asText().isBlank()) {
                    log.debug("JSON login: campo '{}' non trovato, uso '{}' come fallback", path, candidate);
                    return n.asText();
                }
            }

            // 3. Scansione: primo campo con valore che sembra un JWT (inizia con "ey")
            var it = root.fields();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getValue().isTextual() && entry.getValue().asText().startsWith("ey")) {
                    log.debug("JSON login: campo '{}' non trovato, uso '{}' (JWT rilevato automaticamente)", path, entry.getKey());
                    return entry.getValue().asText();
                }
            }

            return null;
        } catch (Exception e) {
            // risposta non JSON — la usiamo direttamente se sembra un JWT
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("ey")) {
                return trimmed;
            }
            return null;
        }
    }

    private long extractTtlFromBody(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode expiresNode = root.path("expires_in");
            if (!expiresNode.isMissingNode() && expiresNode.isNumber()) {
                return expiresNode.asLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** Prova a leggere il claim `exp` dal payload JWT (base64url, secondo segmento). */
    private long extractTtlFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return 0;
            }
            String paddedPayload = parts[1];
            // aggiunge padding se necessario
            int mod = paddedPayload.length() % 4;
            if (mod == 2) paddedPayload += "==";
            else if (mod == 3) paddedPayload += "=";

            byte[] decoded = Base64.getUrlDecoder().decode(paddedPayload);
            JsonNode claims = objectMapper.readTree(decoded);
            JsonNode exp = claims.path("exp");
            if (!exp.isMissingNode() && exp.isNumber()) {
                long ttl = exp.asLong() - Instant.now().getEpochSecond();
                return ttl > 0 ? ttl : 0;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
