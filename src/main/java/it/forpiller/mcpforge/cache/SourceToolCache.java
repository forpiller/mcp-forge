package it.forpiller.mcpforge.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "source_tool_cache", indexes = {
        @Index(name = "idx_tool_cache_name", columnList = "source_name", unique = true)
})
public class SourceToolCache {

    @Id
    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "operations_json", nullable = false, columnDefinition = "TEXT")
    private String operationsJson;

    @Column(name = "spec_hash", length = 64)
    private String specHash;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    protected SourceToolCache() {}

    public SourceToolCache(String sourceName, String operationsJson, String specHash) {
        this.sourceName = sourceName;
        this.operationsJson = operationsJson;
        this.specHash = specHash;
        this.cachedAt = Instant.now();
    }

    public void update(String operationsJson, String specHash) {
        this.operationsJson = operationsJson;
        this.specHash = specHash;
        this.cachedAt = Instant.now();
    }

    public String getSourceName() { return sourceName; }
    public String getOperationsJson() { return operationsJson; }
    public String getSpecHash() { return specHash; }
    public Instant getCachedAt() { return cachedAt; }
}
