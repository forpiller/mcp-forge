package it.forpiller.mcpforge.web.dto;

import it.forpiller.mcpforge.history.SourceRegistrationHistory;

public class SourceRegistrationHistoryDto {

    private final Long id;
    private final String name;
    private final String openApiUrl;
    private final String baseUrl;
    private final String authHeaderName;
    private final String oauth2TokenUrl;
    private final String oauth2ClientId;
    private final String oauth2Username;
    private final String jsonLoginUrl;
    private final String jsonLoginUsername;

    public SourceRegistrationHistoryDto(SourceRegistrationHistory h) {
        this.id = h.getId();
        this.name = h.getName();
        this.openApiUrl = h.getOpenApiUrl();
        this.baseUrl = h.getBaseUrl();
        this.authHeaderName = h.getAuthHeaderName();
        this.oauth2TokenUrl = h.getOauth2TokenUrl();
        this.oauth2ClientId = h.getOauth2ClientId();
        this.oauth2Username = h.getOauth2Username();
        this.jsonLoginUrl = h.getJsonLoginUrl();
        this.jsonLoginUsername = h.getJsonLoginUsername();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getOpenApiUrl() { return openApiUrl; }
    public String getBaseUrl() { return baseUrl; }
    public String getAuthHeaderName() { return authHeaderName; }
    public String getOauth2TokenUrl() { return oauth2TokenUrl; }
    public String getOauth2ClientId() { return oauth2ClientId; }
    public String getOauth2Username() { return oauth2Username; }
    public String getJsonLoginUrl() { return jsonLoginUrl; }
    public String getJsonLoginUsername() { return jsonLoginUsername; }

    public boolean hasOAuth2() {
        return oauth2TokenUrl != null && !oauth2TokenUrl.isBlank();
    }

    public boolean hasJsonLogin() {
        return jsonLoginUrl != null && !jsonLoginUrl.isBlank();
    }
}
