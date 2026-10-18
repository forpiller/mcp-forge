package it.forpiller.mcpforge.history;

import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
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

    @Column(name = "auth_header_value", length = 4000)
    private String authHeaderValue;

    @Column(name = "oauth2_token_url")
    private String oauth2TokenUrl;

    @Column(name = "oauth2_client_id")
    private String oauth2ClientId;

    @Column(name = "oauth2_client_secret", length = 1000)
    private String oauth2ClientSecret;

    @Column(name = "oauth2_username")
    private String oauth2Username;

    @Column(name = "oauth2_password", length = 1000)
    private String oauth2Password;

    @Column(name = "json_login_url")
    private String jsonLoginUrl;

    @Column(name = "json_login_username")
    private String jsonLoginUsername;

    @Column(name = "json_login_password", length = 1000)
    private String jsonLoginPassword;

    @Column(name = "json_login_token_path")
    private String jsonLoginTokenPath;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "start_command", columnDefinition = "TEXT")
    private String startCommand;

    @Column(name = "working_dir", length = 1000)
    private String workingDir;

    @Column(name = "jdk_version", length = 10)
    private String jdkVersion;

    @Column(name = "env_vars", columnDefinition = "TEXT")
    private String envVars;

    public SourceRegistrationHistory() {
    }

    public SourceRegistrationHistory(SourceRegistrationInput input) {
        this.name = input.name();
        applyFrom(input);
    }

    public void updateFrom(SourceRegistrationInput input) {
        applyFrom(input);
    }

    private void applyFrom(SourceRegistrationInput input) {
        this.openApiUrl = input.openApiUrl();
        this.baseUrl = input.baseUrl();
        this.authHeaderName = input.authHeaderName();
        this.authHeaderValue = input.authHeaderValue();
        this.oauth2TokenUrl = input.oauth2TokenUrl();
        this.oauth2ClientId = input.oauth2ClientId();
        this.oauth2ClientSecret = input.oauth2ClientSecret();
        this.oauth2Username = input.oauth2Username();
        this.oauth2Password = input.oauth2Password();
        this.jsonLoginUrl = input.jsonLoginUrl();
        this.jsonLoginUsername = input.jsonLoginUsername();
        this.jsonLoginPassword = input.jsonLoginPassword();
        this.jsonLoginTokenPath = input.jsonLoginTokenPath();
        this.lastUsedAt = Instant.now();
    }

    public boolean hasOAuth2() {
        return oauth2TokenUrl != null && !oauth2TokenUrl.isBlank();
    }

    public boolean hasJsonLogin() {
        return jsonLoginUrl != null && !jsonLoginUrl.isBlank();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOpenApiUrl() { return openApiUrl; }
    public void setOpenApiUrl(String openApiUrl) { this.openApiUrl = openApiUrl; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getAuthHeaderName() { return authHeaderName; }
    public String getAuthHeaderValue() { return authHeaderValue; }
    public String getOauth2TokenUrl() { return oauth2TokenUrl; }
    public String getOauth2ClientId() { return oauth2ClientId; }
    public String getOauth2ClientSecret() { return oauth2ClientSecret; }
    public String getOauth2Username() { return oauth2Username; }
    public String getOauth2Password() { return oauth2Password; }
    public String getJsonLoginUrl() { return jsonLoginUrl; }
    public String getJsonLoginUsername() { return jsonLoginUsername; }
    public String getJsonLoginPassword() { return jsonLoginPassword; }
    public String getJsonLoginTokenPath() { return jsonLoginTokenPath; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }
    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }
    public String getJdkVersion() { return jdkVersion; }
    public void setJdkVersion(String jdkVersion) { this.jdkVersion = jdkVersion; }
    public String getEnvVars() { return envVars; }
    public void setEnvVars(String envVars) { this.envVars = envVars; }

    public SourceRegistrationInput toInput() {
        return new SourceRegistrationInput(
                name, openApiUrl, baseUrl, authHeaderName, authHeaderValue,
                oauth2TokenUrl, oauth2ClientId, oauth2ClientSecret, oauth2Username, oauth2Password,
                jsonLoginUrl, jsonLoginUsername, jsonLoginPassword, jsonLoginTokenPath);
    }
}
