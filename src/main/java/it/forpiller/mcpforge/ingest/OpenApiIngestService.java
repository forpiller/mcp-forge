package it.forpiller.mcpforge.ingest;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Legge uno spec OpenAPI (URL o path locale) e lo trasforma in una lista
 * di operazioni "neutre", pronte per essere mappate su tool MCP.
 */
@Service
public class OpenApiIngestService {

    public List<OperationDescriptor> ingest(String specLocation) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIParser().readLocation(specLocation, null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new IllegalArgumentException(
                    "Impossibile leggere lo spec OpenAPI da " + specLocation + ": " + result.getMessages());
        }

        List<OperationDescriptor> descriptors = new ArrayList<>();
        if (openApi.getPaths() == null) {
            return descriptors;
        }

        openApi.getPaths().forEach((path, pathItem) -> {
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : pathItem.readOperationsMap().entrySet()) {
                descriptors.add(toDescriptor(entry.getKey().name(), path, entry.getValue()));
            }
        });

        return descriptors;
    }

    private OperationDescriptor toDescriptor(String httpMethod, String path, Operation operation) {
        String toolName = sanitizeToolName(
                Objects.requireNonNullElse(operation.getOperationId(), httpMethod + "_" + path));

        List<ParamDescriptor> params = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                params.add(toParamDescriptor(parameter));
            }
        }

        boolean hasRequestBody = operation.getRequestBody() != null;
        String requestBodyDescription = hasRequestBody
                ? operation.getRequestBody().getDescription()
                : null;

        String summary = operation.getSummary() != null
                ? operation.getSummary()
                : operation.getDescription();

        return new OperationDescriptor(toolName, httpMethod, path, summary, params, hasRequestBody, requestBodyDescription);
    }

    private ParamDescriptor toParamDescriptor(Parameter parameter) {
        String type = "string";
        if (parameter.getSchema() != null && parameter.getSchema().getType() != null) {
            type = parameter.getSchema().getType();
        }

        ParamLocation location = switch (parameter.getIn()) {
            case "path" -> ParamLocation.PATH;
            case "header" -> ParamLocation.HEADER;
            default -> ParamLocation.QUERY;
        };

        boolean required = Boolean.TRUE.equals(parameter.getRequired());

        return new ParamDescriptor(parameter.getName(), location, required, type, parameter.getDescription());
    }

    private String sanitizeToolName(String raw) {
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }
}
