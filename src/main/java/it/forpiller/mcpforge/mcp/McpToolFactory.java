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
                .inputSchema(buildInputSchema(registeredTool.operation()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handleCall(registeredTool, request))
                .build();
    }

    private McpSchema.CallToolResult handleCall(RegisteredTool registeredTool, McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
        String sourceName = registeredTool.source().name();
        String toolName = registeredTool.fullToolName();

        if (!rateLimiterService.tryConsume(sourceName, toolName)) {
            auditLogService.record(sourceName, toolName, arguments, null, 0, false, "rate limit superato");
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Rate limit superato per '" + toolName + "'. Riprova più tardi.")
                    .isError(true)
                    .build();
        }

        long start = System.currentTimeMillis();
        ApiCallResult result = apiInvoker.invoke(registeredTool, arguments);
        long durationMs = System.currentTimeMillis() - start;

        auditLogService.record(sourceName, toolName, arguments, result.httpStatus(), durationMs,
                result.success(), result.errorMessage());

        if (result.success()) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(result.body() != null ? result.body() : "")
                    .build();
        }

        return McpSchema.CallToolResult.builder()
                .addTextContent("Errore chiamando '" + toolName + "': " + result.errorMessage())
                .isError(true)
                .build();
    }

    private String buildDescription(RegisteredTool registeredTool) {
        OperationDescriptor op = registeredTool.operation();
        String summary = op.summary() != null ? op.summary() : (op.httpMethod() + " " + op.path());
        return "[" + registeredTool.source().name() + "] " + summary;
    }

    private McpSchema.JsonSchema buildInputSchema(OperationDescriptor operation) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParamDescriptor param : operation.parameters()) {
            Map<String, Object> propertySchema = new LinkedHashMap<>();
            propertySchema.put("type", mapJsonSchemaType(param.jsonSchemaType()));
            if (param.description() != null) {
                propertySchema.put("description", param.description());
            }
            properties.put(param.name(), propertySchema);
            if (param.required()) {
                required.add(param.name());
            }
        }

        if (operation.hasRequestBody()) {
            Map<String, Object> bodySchema = new LinkedHashMap<>();
            bodySchema.put("type", "object");
            bodySchema.put("description", operation.requestBodyDescription() != null
                    ? operation.requestBodyDescription()
                    : "Corpo della richiesta");
            properties.put("body", bodySchema);
        }

        return McpSchema.JsonSchema.builder()
                .type("object")
                .properties(properties)
                .required(required)
                .build();
    }

    private String mapJsonSchemaType(String openApiType) {
        return openApiType != null && JSON_SCHEMA_TYPES.contains(openApiType) ? openApiType : "string";
    }
}
