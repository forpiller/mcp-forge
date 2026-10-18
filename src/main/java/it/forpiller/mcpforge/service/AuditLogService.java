package it.forpiller.mcpforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.forpiller.mcpforge.audit.AuditLogEntry;
import it.forpiller.mcpforge.config.McpForgeProperties;
import it.forpiller.mcpforge.repository.AuditLogRepository;
import it.forpiller.mcpforge.web.dto.AuditLogEntryDto;
import it.forpiller.mcpforge.web.dto.AuditLogRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditLogService {

    private static final String REDACTED = "***";

    private final AuditLogRepository auditLogRepository;
    private final McpForgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(AuditLogRepository auditLogRepository, McpForgeProperties properties) {
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    public void record(AuditLogRequest request) {
        String inputParamsJson = toRedactedJson(request.inputParams());
        AuditLogEntry entry = new AuditLogEntry(
                Instant.now(),
                request.sourceName(),
                request.toolName(),
                inputParamsJson,
                request.httpStatus(),
                request.durationMs(),
                request.success(),
                request.errorMessage());
        auditLogRepository.save(entry);
    }

    public Page<AuditLogEntryDto> findAll(int page, int size) {
        return auditLogRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(AuditLogEntryDto::new);
    }

    public Page<AuditLogEntryDto> findBySourceName(String sourceName, int page, int size) {
        return auditLogRepository
                .findBySourceName(sourceName, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(AuditLogEntryDto::new);
    }

    public List<String> findDistinctSourceNames() {
        return auditLogRepository.findDistinctSourceNames();
    }

    public boolean isSourceHealthy(String sourceName, Instant registeredAt) {
        List<AuditLogEntry> latest = auditLogRepository
                .findBySourceName(sourceName, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        return latest.isEmpty()
                || latest.get(0).getCreatedAt().isBefore(registeredAt)
                || latest.get(0).isSuccess();
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
                .anyMatch(r -> r.equalsIgnoreCase(fieldName));
    }
}
