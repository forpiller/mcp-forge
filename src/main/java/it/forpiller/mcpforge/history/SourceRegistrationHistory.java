package it.forpiller.mcpforge.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Salva solo gli INPUT usati per registrare una fonte (non i tool generati,
 * che sono ricavabili rileggendo lo spec OpenAPI) — così si può ri-registrare
 * una fonte con un click dopo un riavvio, senza riscrivere il form.
 */
@Entity
@Table(name = "source_registration_history", indexes = {
        @Index(name = "idx_history_name", columnList = "name", unique = true)
})
public class SourceRegistrationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "open_api_url", nullable = false)
    private String openApiUrl;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "auth_header_name")
    private String authHeaderName;

    @Column(name = "auth_header_value")
    private String authHeaderValue;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    protected SourceRegistrationHistory() {
        // richiesto da JPA
    }

    public SourceRegistrationHistory(String name, String openApiUrl, String baseUrl,
                                      String authHeaderName, String authHeaderValue) {
        this.name = name;
        this.openApiUrl = openApiUrl;
        this.baseUrl = baseUrl;
        this.authHeaderName = authHeaderName;
        this.authHeaderValue = authHeaderValue;
        this.lastUsedAt = Instant.now();
    }

    public void updateFrom(String openApiUrl, String baseUrl, String authHeaderName, String authHeaderValue) {
        this.openApiUrl = openApiUrl;
        this.baseUrl = baseUrl;
        this.authHeaderName = authHeaderName;
        this.authHeaderValue = authHeaderValue;
        this.lastUsedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOpenApiUrl() {
        return openApiUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public String getAuthHeaderValue() {
        return authHeaderValue;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
