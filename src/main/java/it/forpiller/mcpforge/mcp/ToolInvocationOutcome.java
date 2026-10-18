package it.forpiller.mcpforge.mcp;

public record ToolInvocationOutcome(
        boolean success,
        String text
) {
}
