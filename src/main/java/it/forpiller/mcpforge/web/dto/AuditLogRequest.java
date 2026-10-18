package it.forpiller.mcpforge.web.dto;

import java.util.Map;

public record AuditLogRequest(
        String sourceName,
        String toolName,
        Map<String, Object> inputParams,
        Integer httpStatus,
        long durationMs,
        boolean success,
        String errorMessage
) {
}
