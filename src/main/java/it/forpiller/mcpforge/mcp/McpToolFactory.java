package it.forpiller.mcpforge.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import it.forpiller.mcpforge.audit.AuditLogService;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.ingest.ParamDescriptor;
import it.forpiller.mcpforge.ratelimit.RateLimiterService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trasforma un'operazione OpenAPI (già ingerita) in una specifica di tool
 * MCP pronta per essere registrata su {@link io.modelcontextprotocol.server.McpSyncServer},
 * con rate limiting e audit log applicati ad ogni invocazione.
 */
@Component
public class McpToolFactory {

    private static final Set<String> JSON_SCHEMA_TYPES = Set.of("integer", "number", "boolean", "array", "object");

    private final ApiInvoker apiInvoker;
    private final RateLimiterService rateLimiterService;
    private final AuditLogService auditLogService;

    public McpToolFactory(ApiInvoker apiInvoker, RateLimiterService rateLimiterService,
                           AuditLogService auditLogService) {
        this.apiInvoker = apiInvoker;
        this.rateLimiterService = rateLimiterService;
        this.auditLogService = auditLogService;
    }

    public McpServerFeatures.SyncToolSpecification buildSpecification(RegisteredTool registeredTool) {
        McpSchema.Tool tool = McpSchema.Tool.builder(registeredTool.fullToolName())
                .description(buildDescription(registeredTool))
                .inputSchema(McpSchema.JsonSchema.builder()
                        .type("object")
                        .properties(buildInputSchemaProperties(registeredTool.operation()))
                        .required(buildRequiredFields(registeredTool.operation()))
                        .build())
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
                    ToolInvocationOutcome outcome = invokeWithGuards(registeredTool, arguments);
                    return McpSchema.CallToolResult.builder()
                            .addTextContent(outcome.text())
                            .isError(!outcome.success())
                            .build();
                })
                .build();
    }

    /**
     * Logica condivisa tra il protocollo MCP e la chat locale (Ollama): rate
     * limit, chiamata reale, audit log — un solo punto da cui passano tutte
     * le invocazioni di tool, qualunque sia il client che le ha innescate.
     */
    public ToolInvocationOutcome invokeWithGuards(RegisteredTool registeredTool, Map<String, Object> arguments) {
        String sourceName = registeredTool.source().name();
        String toolName = registeredTool.fullToolName();

        if (!rateLimiterService.tryConsume(sourceName, toolName)) {
            auditLogService.record(sourceName, toolName, arguments, null, 0, false, "rate limit superato");
            return new ToolInvocationOutcome(false, "Rate limit superato per '" + toolName + "'. Riprova più tardi.");
        }

        long start = System.currentTimeMillis();
        ApiCallResult result = apiInvoker.invoke(registeredTool, arguments);
        long durationMs = System.currentTimeMillis() - start;

        auditLogService.record(sourceName, toolName, arguments, result.httpStatus(), durationMs,
                result.success(), result.errorMessage());

        if (result.success()) {
            return new ToolInvocationOutcome(true, result.body() != null ? result.body() : "");
        }

        return new ToolInvocationOutcome(false, "Errore chiamando '" + toolName + "': " + result.errorMessage());
    }

    public String buildDescription(RegisteredTool registeredTool) {
        OperationDescriptor op = registeredTool.operation();
        String summary = op.summary() != null ? op.summary() : (op.httpMethod() + " " + op.path());
        return "[" + registeredTool.source().name() + "] " + summary;
    }

    public Map<String, Object> buildInputSchemaProperties(OperationDescriptor operation) {
        Map<String, Object> properties = new LinkedHashMap<>();

        for (ParamDescriptor param : operation.parameters()) {
            Map<String, Object> propertySchema = new LinkedHashMap<>();
            propertySchema.put("type", mapJsonSchemaType(param.jsonSchemaType()));
            if (param.description() != null) {
                propertySchema.put("description", param.description());
            }
            properties.put(param.name(), propertySchema);
        }

        if (operation.hasRequestBody()) {
            Map<String, Object> bodySchema = new LinkedHashMap<>();
            bodySchema.put("type", "object");
            bodySchema.put("description", operation.requestBodyDescription() != null
                    ? operation.requestBodyDescription()
                    : "Corpo della richiesta");
            properties.put("body", bodySchema);
        }

        return properties;
    }

    public List<String> buildRequiredFields(OperationDescriptor operation) {
        List<String> required = new ArrayList<>();
        for (ParamDescriptor param : operation.parameters()) {
            if (param.required()) {
                required.add(param.name());
            }
        }
        return required;
    }

    private String mapJsonSchemaType(String openApiType) {
        return openApiType != null && JSON_SCHEMA_TYPES.contains(openApiType) ? openApiType : "string";
    }
}
