package it.forpiller.mcpforge.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_source_name", columnList = "source_name"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Lob
    @Column(name = "input_params")
    private String inputParamsJson;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message")
    private String errorMessage;

    protected AuditLogEntry() {
        // richiesto da JPA
    }

    public AuditLogEntry(Instant createdAt, String sourceName, String toolName, String inputParamsJson,
                          Integer httpStatus, long durationMs, boolean success, String errorMessage) {
        this.createdAt = createdAt;
        this.sourceName = sourceName;
        this.toolName = toolName;
        this.inputParamsJson = inputParamsJson;
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getToolName() {
        return toolName;
    }

    public String getInputParamsJson() {
        return inputParamsJson;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
