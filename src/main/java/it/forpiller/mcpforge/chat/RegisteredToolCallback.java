package it.forpiller.mcpforge.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.forpiller.mcpforge.mcp.McpToolFactory;
import it.forpiller.mcpforge.mcp.RegisteredTool;
import it.forpiller.mcpforge.mcp.ToolInvocationOutcome;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adatta un {@link RegisteredTool} (già usato per il protocollo MCP) a un
 * {@link ToolCallback} di Spring AI, così lo stesso tool è richiamabile sia
 * da un client MCP esterno sia dal ChatClient locale (Ollama). La chiamata
 * vera passa sempre da {@link McpToolFactory#invokeWithGuards} — stesso
 * rate limit e stesso audit log, qualunque sia il chiamante.
 */
public class RegisteredToolCallback implements ToolCallback {

    private final RegisteredTool registeredTool;
    private final McpToolFactory mcpToolFactory;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public RegisteredToolCallback(RegisteredTool registeredTool, McpToolFactory mcpToolFactory,
                                   ObjectMapper objectMapper) {
        this.registeredTool = registeredTool;
        this.mcpToolFactory = mcpToolFactory;
        this.objectMapper = objectMapper;

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", mcpToolFactory.buildInputSchemaProperties(registeredTool.operation()));
        schema.put("required", mcpToolFactory.buildRequiredFields(registeredTool.operation()));

        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            schemaJson = "{\"type\":\"object\",\"properties\":{}}";
        }

        this.toolDefinition = ToolDefinition.builder()
                .name(registeredTool.fullToolName())
                .description(mcpToolFactory.buildDescription(registeredTool))
                .inputSchema(schemaJson)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> arguments;
        try {
            arguments = objectMapper.readValue(toolInput, Map.class);
        } catch (Exception e) {
            arguments = Map.of();
        }

        ToolInvocationOutcome outcome = mcpToolFactory.invokeWithGuards(registeredTool, arguments);
        return outcome.text();
    }
}
