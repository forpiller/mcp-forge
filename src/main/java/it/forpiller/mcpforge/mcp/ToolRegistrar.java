package it.forpiller.mcpforge.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestratore: per ogni operazione ingerita da uno spec OpenAPI, genera
 * il tool MCP corrispondente e lo registra a runtime sul server MCP già
 * avviato — nessun riavvio necessario, i client connessi vengono notificati.
 */
public class ToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistrar.class);

    private final SourceRegistry sourceRegistry;
    private final McpToolFactory mcpToolFactory;
    private final McpSyncServer mcpSyncServer;

    public ToolRegistrar(SourceRegistry sourceRegistry, McpToolFactory mcpToolFactory, McpSyncServer mcpSyncServer) {
        this.sourceRegistry = sourceRegistry;
        this.mcpToolFactory = mcpToolFactory;
        this.mcpSyncServer = mcpSyncServer;
    }

    public List<String> unregisterSource(String sourceName) {
        List<String> removed = sourceRegistry.removeBySourceName(sourceName);
        removed.forEach(mcpSyncServer::removeTool);
        if (!removed.isEmpty()) {
            mcpSyncServer.notifyToolsListChanged();
        }
        return removed;
    }

    public List<String> registerSource(SourceConfig source, List<OperationDescriptor> operations) {
        List<String> toolNames = new ArrayList<>();
        Instant registeredAt = Instant.now();

        for (OperationDescriptor operation : operations) {
            String fullToolName = sanitize(source.name() + "__" + operation.toolName());
            RegisteredTool registeredTool = new RegisteredTool(fullToolName, source, operation, registeredAt);

            sourceRegistry.register(registeredTool);
            mcpSyncServer.addTool(mcpToolFactory.buildSpecification(registeredTool));
            toolNames.add(fullToolName);
        }

        mcpSyncServer.notifyToolsListChanged();
        return toolNames;
    }

    private String sanitize(String raw) {
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 64) {
            log.warn("Nome tool troncato a 64 char: '{}' → '{}'", sanitized, sanitized.substring(0, 64));
            return sanitized.substring(0, 64);
        }
        return sanitized;
    }
}
