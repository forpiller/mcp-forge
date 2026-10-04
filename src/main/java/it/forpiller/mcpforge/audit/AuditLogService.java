package it.forpiller.mcpforge.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.forpiller.mcpforge.config.McpForgeProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogService {

    private static final String REDACTED = "***";

    private final AuditLogRepository auditLogRepository;
    private final McpForgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(AuditLogRepository auditLogRepository, McpForgeProperties properties) {
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    public void record(String sourceName, String toolName, Map<String, Object> inputParams,
                        Integer httpStatus, long durationMs, boolean success, String errorMessage) {
        String inputParamsJson = toRedactedJson(inputParams);
        AuditLogEntry entry = new AuditLogEntry(
                Instant.now(), sourceName, toolName, inputParamsJson, httpStatus, durationMs, success, errorMessage);
        auditLogRepository.save(entry);
    }

    private String toRedactedJson(Map<String, Object> inputParams) {
        if (inputParams == null || inputParams.isEmpty()) {
            return "{}";
        }

        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
            redacted.put(entry.getKey(), isSensitive(entry.getKey()) ? REDACTED : entry.getValue());
        }

        try {
            return objectMapper.writeValueAsString(redacted);
        } catch (Exception e) {
            return "{\"error\":\"serializzazione input fallita\"}";
        }
    }

    private boolean isSensitive(String fieldName) {
        return properties.getAudit().getRedactFields().stream()
                .anyMatch(redacted -> redacted.equalsIgnoreCase(fieldName));
    }
}
