package it.forpiller.mcpforge.mcp;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiene traccia, in memoria, delle fonti API registrate e dei tool MCP
 * generati per ciascuna — necessario per risalire da un nome di tool
 * invocato dal client MCP alla fonte/operazione reale da chiamare.
 */
@Component
public class SourceRegistry {

    private final ConcurrentHashMap<String, RegisteredTool> toolsByName = new ConcurrentHashMap<>();

    public void register(RegisteredTool tool) {
        toolsByName.put(tool.fullToolName(), tool);
    }

    public Optional<RegisteredTool> find(String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    public List<RegisteredTool> all() {
        return List.copyOf(toolsByName.values());
    }
}
