package it.forpiller.mcpforge.web;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Tenta di individuare automaticamente una spec OpenAPI/Swagger dato un base URL,
 * provando i path più comuni in sequenza con timeout breve.
 */
@RestController
public class ProbeController {

    private static final String[] PROBE_PATHS = {
        "/v3/api-docs",
        "/v2/api-docs",
        "/api-docs",
        "/openapi.json",
        "/openapi.yaml",
        "/swagger.json",
        "/swagger.yaml",
        "/swagger/v1/swagger.json",
        "/api/swagger.json",
        "/api/openapi.json",
        "/api/v1/api-docs",
    };

    private static final String[] SWAGGER_UI_PATHS = {
        // Spring Boot 3 + springdoc-openapi v2
        "/v3/swagger-ui/index.html",
        "/v3/swagger-ui.html",
        // Spring Boot 2 + springdoc-openapi v1
        "/swagger-ui/index.html",
        "/swagger-ui.html",
        "/swagger-ui/",
        // Springfox legacy / Spring MVC classico
        "/v2/swagger-ui.html",
        "/v2/swagger-ui/index.html",
        "/v1/swagger-ui.html",
        "/v1/swagger-ui/index.html",
        "/webjars/swagger-ui/index.html",
        "/webjars/swagger-ui/2.9.2/index.html",
        "/webjars/swagger-ui/3.52.5/index.html",
        // Con prefisso /api
        "/api/swagger-ui.html",
        "/api/swagger-ui/index.html",
        "/api/v3/swagger-ui/index.html",
        "/api/v2/swagger-ui.html",
        "/api/v1/swagger-ui.html",
        "/api/v1/swagger-ui/index.html",
        "/api/docs",
        // .NET Swashbuckle
        "/swagger",
        "/swagger/index.html",
        "/swagger/ui/index.html",
        // FastAPI / Python
        "/docs",
        "/redoc",
        // Node (swagger-ui-express, tsoa)
        "/api-docs",
        "/api/api-docs",
        // Go (echo, gin-swagger)
        "/swagger/",
        // Percorsi generici
        "/openapi-ui",
        "/openapi-ui/index.html",
        "/documentation",
        "/api/documentation",
    };

    private final RestClient probeClient;

    public ProbeController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.probeClient = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/probe-openapi")
    public Map<String, Object> probe(@RequestParam String baseUrl) {
        String base = baseUrl.replaceAll("/+$", "");

        String specUrl = null;
        for (String path : PROBE_PATHS) {
            String url = base + path;
            try {
                String body = probeClient.get().uri(url).retrieve().body(String.class);
                if (looksLikeOpenApi(body)) {
                    specUrl = url;
                    break;
                }
            } catch (Exception ignored) {}
        }

        String swaggerUiUrl = null;
        for (String path : SWAGGER_UI_PATHS) {
            String url = base + path;
            try {
                String body = probeClient.get().uri(url).retrieve().body(String.class);
                if (looksLikeSwaggerUi(body)) {
                    swaggerUiUrl = url;
                    break;
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("found", specUrl != null);
        if (specUrl != null) result.put("url", specUrl);
        if (swaggerUiUrl != null) result.put("swaggerUiUrl", swaggerUiUrl);
        return result;
    }

    private boolean looksLikeOpenApi(String body) {
        if (body == null || body.isBlank()) return false;
        return body.contains("\"openapi\"")
                || body.contains("\"swagger\"")
                || body.startsWith("openapi:")
                || body.startsWith("swagger:");
    }

    private boolean looksLikeSwaggerUi(String body) {
        if (body == null || body.isBlank()) return false;
        String lower = body.toLowerCase();
        return lower.contains("swagger-ui") || lower.contains("swaggeruibundle");
    }
}
