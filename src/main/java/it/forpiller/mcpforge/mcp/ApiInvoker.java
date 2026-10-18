package it.forpiller.mcpforge.mcp;

import it.forpiller.mcpforge.ingest.ParamDescriptor;
import it.forpiller.mcpforge.ingest.ParamLocation;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Esegue la vera chiamata HTTP verso l'API registrata, per conto di un
 * tool MCP generato automaticamente. La chiave riservata "body" negli
 * argomenti, quando presente, viene inviata come request body.
 *
 * Gestisce automaticamente:
 * - retry su 401 con refresh del token (OAuth2 e JSON login)
 * - retry su 403 con refresh del CSRF token (per sorgenti JSON login con
 *   Spring Security CookieCsrfTokenRepository)
 */
public class ApiInvoker {

    private static final String BODY_ARGUMENT_KEY = "body";
    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final RestClient restClient;
    private final OAuth2TokenService oAuth2TokenService;
    private final JsonLoginTokenService jsonLoginTokenService;
    private final CsrfTokenService csrfTokenService;

    public ApiInvoker(RestClient restClient, OAuth2TokenService oAuth2TokenService,
                      JsonLoginTokenService jsonLoginTokenService, CsrfTokenService csrfTokenService) {
        this.restClient = restClient;
        this.oAuth2TokenService = oAuth2TokenService;
        this.jsonLoginTokenService = jsonLoginTokenService;
        this.csrfTokenService = csrfTokenService;
    }

    public ApiCallResult invoke(RegisteredTool tool, Map<String, Object> arguments) {
        try {
            return doInvoke(tool, arguments, true, true);
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            String errMsg = e.getStatusCode().value() + " : " + (body.isBlank() ? e.getStatusText() : body);
            return new ApiCallResult(e.getStatusCode().value(), body, false, errMsg);
        } catch (Exception e) {
            return new ApiCallResult(0, null, false, e.getMessage());
        }
    }

    private ApiCallResult doInvoke(RegisteredTool tool, Map<String, Object> arguments,
                                    boolean allowTokenRetry, boolean allowCsrfRetry) {
        URI uri = buildUri(tool, arguments);
        RestClient.RequestBodySpec requestSpec = restClient
                .method(HttpMethod.valueOf(tool.operation().httpMethod()))
                .uri(uri);

        applyHeaders(requestSpec, tool, arguments);

        try {
            Object body = arguments.get(BODY_ARGUMENT_KEY);
            ResponseEntity<String> response = (tool.operation().hasRequestBody() && body != null
                    ? requestSpec.body(body)
                    : requestSpec)
                    .retrieve()
                    .toEntity(String.class);

            return new ApiCallResult(response.getStatusCode().value(), response.getBody(), true, null);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            if (allowTokenRetry && status == 401) {
                if (tool.source().oauth2() != null) {
                    oAuth2TokenService.invalidate(tool.source().name());
                    return doInvoke(tool, arguments, false, allowCsrfRetry);
                } else if (tool.source().jsonLogin() != null) {
                    jsonLoginTokenService.invalidate(tool.source().name());
                    return doInvoke(tool, arguments, false, allowCsrfRetry);
                }
            }

            if (allowCsrfRetry && status == 403 && tool.source().jsonLogin() != null) {
                csrfTokenService.invalidate(tool.source().name());
                return doInvoke(tool, arguments, allowTokenRetry, false);
            }

            throw e;
        }
    }

    private URI buildUri(RegisteredTool tool, Map<String, Object> arguments) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(tool.source().baseUrl() + tool.operation().path());

        Map<String, Object> pathVariables = new HashMap<>();
        for (ParamDescriptor param : tool.operation().parameters()) {
            Object value = arguments.get(param.name());
            if (value == null) continue;
            switch (param.location()) {
                case PATH -> pathVariables.put(param.name(), value);
                case QUERY -> {
                    if (value instanceof Map<?, ?> map) {
                        // Flatten object-type query params: {title:"Sport"} → ?title=Sport
                        map.forEach((k, v) -> {
                            if (v != null) builder.queryParam(String.valueOf(k), v);
                        });
                    } else if (value instanceof Iterable<?> iter) {
                        iter.forEach(v -> builder.queryParam(param.name(), v));
                    } else {
                        builder.queryParam(param.name(), value);
                    }
                }
                case HEADER -> { /* gestiti in applyHeaders */ }
            }
        }

        return builder.buildAndExpand(pathVariables).toUri();
    }

    private void applyHeaders(RestClient.RequestBodySpec requestSpec, RegisteredTool tool,
                               Map<String, Object> arguments) {
        for (ParamDescriptor param : tool.operation().parameters()) {
            if (param.location() == ParamLocation.HEADER) {
                Object value = arguments.get(param.name());
                if (value != null) requestSpec.header(param.name(), String.valueOf(value));
            }
        }

        SourceConfig source = tool.source();
        if (source.oauth2() != null && source.oauth2().isConfigured()) {
            String token = oAuth2TokenService.getAccessToken(source.name(), source.oauth2());
            String headerName = source.authHeaderName() != null && !source.authHeaderName().isBlank()
                    ? source.authHeaderName() : "Authorization";
            requestSpec.header(headerName, "Bearer " + token);

        } else if (source.jsonLogin() != null && source.jsonLogin().isConfigured()) {
            String token = jsonLoginTokenService.getAccessToken(source.name(), source.jsonLogin());
            requestSpec.header("Authorization", "Bearer " + token);

            if (CSRF_METHODS.contains(tool.operation().httpMethod().toUpperCase())) {
                CsrfTokenService.CsrfEntry csrf = csrfTokenService.getOrFetch(source.name(), source.baseUrl());
                if (csrf != null) {
                    requestSpec.header("X-XSRF-TOKEN", csrf.token());
                    requestSpec.header("Cookie", csrf.cookieHeader());
                    requestSpec.header("Origin", source.baseUrl());
                    requestSpec.header("Referer", source.baseUrl() + "/");
                }
            }

        } else if (source.authHeaderName() != null && !source.authHeaderName().isBlank()) {
            requestSpec.header(source.authHeaderName(), source.authHeaderValue());
        }
    }
}
