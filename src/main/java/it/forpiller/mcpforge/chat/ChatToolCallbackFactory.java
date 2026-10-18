package it.forpiller.mcpforge.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.forpiller.mcpforge.mcp.McpToolFactory;
import it.forpiller.mcpforge.mcp.SourceRegistry;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

public class ChatToolCallbackFactory {

    private final SourceRegistry sourceRegistry;
    private final McpToolFactory mcpToolFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatToolCallbackFactory(SourceRegistry sourceRegistry, McpToolFactory mcpToolFactory) {
        this.sourceRegistry = sourceRegistry;
        this.mcpToolFactory = mcpToolFactory;
    }

    public List<ToolCallback> buildCallbacks() {
        return buildCallbacks(null);
    }

    /**
     * @param selectedNames se non null, limita ai tool con questi nomi esatti
     *                      (selezione fatta dall'utente nella pagina chat)
     */
    public List<ToolCallback> buildCallbacks(Set<String> selectedNames) {
        return sourceRegistry.all().stream()
                .filter(tool -> selectedNames == null || selectedNames.contains(tool.fullToolName()))
                .map(tool -> (ToolCallback) new RegisteredToolCallback(tool, mcpToolFactory, objectMapper))
                .toList();
    }
}
