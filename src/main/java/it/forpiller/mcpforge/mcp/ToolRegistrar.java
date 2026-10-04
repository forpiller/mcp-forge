package it.forpiller.mcpforge.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestratore: per ogni operazione ingerita da uno spec OpenAPI, genera
 * il tool MCP corrispondente e lo registra a runtime sul server MCP già
 * avviato — nessun riavvio necessario, i client connessi vengono notificati.
 */
@Service
public class ToolRegistrar {

    private final SourceRegistry sourceRegistry;
    private final McpToolFactory mcpToolFactory;
    private final McpSyncServer mcpSyncServer;

    public ToolRegistrar(SourceRegistry sourceRegistry, McpToolFactory mcpToolFactory, McpSyncServer mcpSyncServer) {
        this.sourceRegistry = sourceRegistry;
        this.mcpToolFactory = mcpToolFactory;
        this.mcpSyncServer = mcpSyncServer;
    }

    public List<String> registerSource(SourceConfig source, List<OperationDescriptor> operations) {
        List<String> toolNames = new ArrayList<>();

        for (OperationDescriptor operation : operations) {
            String fullToolName = sanitize(source.name() + "__" + operation.toolName());
            RegisteredTool registeredTool = new RegisteredTool(fullToolName, source, operation);

            sourceRegistry.register(registeredTool);
            mcpSyncServer.addTool(mcpToolFactory.buildSpecification(registeredTool));
            toolNames.add(fullToolName);
        }

        mcpSyncServer.notifyToolsListChanged();
        return toolNames;
    }

    private String sanitize(String raw) {
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }
}
