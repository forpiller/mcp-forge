package it.forpiller.mcpforge.mcp;

import it.forpiller.mcpforge.ingest.ParamDescriptor;
import it.forpiller.mcpforge.ingest.ParamLocation;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Esegue la vera chiamata HTTP verso l'API registrata, per conto di un
 * tool MCP generato automaticamente. La chiave riservata "body" negli
 * argomenti, quando presente, viene inviata come request body.
 */
@Component
public class ApiInvoker {

    private static final String BODY_ARGUMENT_KEY = "body";

    private final RestClient restClient = RestClient.create();

    public ApiCallResult invoke(RegisteredTool tool, Map<String, Object> arguments) {
        try {
            URI uri = buildUri(tool, arguments);
            RestClient.RequestBodySpec requestSpec = restClient
                    .method(HttpMethod.valueOf(tool.operation().httpMethod()))
                    .uri(uri);

            applyHeaders(requestSpec, tool, arguments);

            Object body = arguments.get(BODY_ARGUMENT_KEY);
            String responseBody = (tool.operation().hasRequestBody() && body != null
                    ? requestSpec.body(body)
                    : requestSpec)
                    .retrieve()
                    .body(String.class);

            return new ApiCallResult(200, responseBody, true, null);
        } catch (RestClientResponseException e) {
            return new ApiCallResult(e.getStatusCode().value(), e.getResponseBodyAsString(), false, e.getMessage());
        } catch (Exception e) {
            return new ApiCallResult(0, null, false, e.getMessage());
        }
    }

    private URI buildUri(RegisteredTool tool, Map<String, Object> arguments) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(tool.source().baseUrl() + tool.operation().path());

        Map<String, Object> pathVariables = new HashMap<>();
        for (ParamDescriptor param : tool.operation().parameters()) {
            Object value = arguments.get(param.name());
            if (value == null) {
                continue;
            }
            switch (param.location()) {
                case PATH -> pathVariables.put(param.name(), value);
                case QUERY -> builder.queryParam(param.name(), value);
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
                if (value != null) {
                    requestSpec.header(param.name(), String.valueOf(value));
                }
            }
        }

        SourceConfig source = tool.source();
        if (source.authHeaderName() != null && !source.authHeaderName().isBlank()) {
            requestSpec.header(source.authHeaderName(), source.authHeaderValue());
        }
    }
}
