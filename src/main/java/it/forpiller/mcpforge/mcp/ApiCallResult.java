package it.forpiller.mcpforge.mcp;

public record ApiCallResult(
        int httpStatus,
        String body,
        boolean success,
        String errorMessage
) {
}
