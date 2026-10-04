package it.forpiller.mcpforge.mcp;

import it.forpiller.mcpforge.ingest.OperationDescriptor;

public record RegisteredTool(
        String fullToolName,
        SourceConfig source,
        OperationDescriptor operation
) {
}
