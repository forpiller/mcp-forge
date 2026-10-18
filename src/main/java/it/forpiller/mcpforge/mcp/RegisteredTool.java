package it.forpiller.mcpforge.mcp;

import it.forpiller.mcpforge.ingest.OperationDescriptor;

import java.time.Instant;

public record RegisteredTool(
        String fullToolName,
        SourceConfig source,
        OperationDescriptor operation,
        Instant registeredAt
) {
}
