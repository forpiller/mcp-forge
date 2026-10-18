package it.forpiller.mcpforge.web.dto;

import it.forpiller.mcpforge.audit.AuditLogEntry;

import java.time.Instant;

public class AuditLogEntryDto {

    private final Instant createdAt;
    private final String sourceName;
    private final String toolName;
    private final Integer httpStatus;
    private final long durationMs;
    private final boolean success;
    private final String errorMessage;

    public AuditLogEntryDto(AuditLogEntry e) {
        this.createdAt = e.getCreatedAt();
        this.sourceName = e.getSourceName();
        this.toolName = e.getToolName();
        this.httpStatus = e.getHttpStatus();
        this.durationMs = e.getDurationMs();
        this.success = e.isSuccess();
        this.errorMessage = e.getErrorMessage();
    }

    public Instant getCreatedAt() { return createdAt; }
    public String getSourceName() { return sourceName; }
    public String getToolName() { return toolName; }
    public Integer getHttpStatus() { return httpStatus; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
