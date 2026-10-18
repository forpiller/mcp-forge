package it.forpiller.mcpforge.web.dto;

import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
import jakarta.validation.constraints.NotBlank;

/**
 * Versione mutabile di {@link RegisterSourceRequest}, usata per il binding
 * del form Thymeleaf (th:field richiede getter/setter, non un record).
 */
public class RegisterSourceForm {

    @NotBlank(message = "il nome della fonte è obbligatorio")
    private String name;

    @NotBlank(message = "l'URL dello spec OpenAPI è obbligatorio")
    private String openApiUrl;

    @NotBlank(message = "la base URL dell'API è obbligatoria")
    private String baseUrl;

    private String authHeaderName;
    private String authHeaderValue;
    private String oauth2TokenUrl;
    private String oauth2ClientId;
    private String oauth2ClientSecret;
    private String oauth2Username;
    private String oauth2Password;
    private String jsonLoginUrl;
    private String jsonLoginUsername;
    private String jsonLoginPassword;
    private String jsonLoginTokenPath;

    public SourceRegistrationInput toInput() {
        return new SourceRegistrationInput(name, openApiUrl, baseUrl, authHeaderName, authHeaderValue,
                oauth2TokenUrl, oauth2ClientId, oauth2ClientSecret, oauth2Username, oauth2Password,
                jsonLoginUrl, jsonLoginUsername, jsonLoginPassword, jsonLoginTokenPath);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOpenApiUrl() { return openApiUrl; }
    public void setOpenApiUrl(String openApiUrl) { this.openApiUrl = openApiUrl; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getAuthHeaderValue() { return authHeaderValue; }
    public void setAuthHeaderValue(String authHeaderValue) { this.authHeaderValue = authHeaderValue; }
    public String getOauth2TokenUrl() { return oauth2TokenUrl; }
    public void setOauth2TokenUrl(String oauth2TokenUrl) { this.oauth2TokenUrl = oauth2TokenUrl; }
    public String getOauth2ClientId() { return oauth2ClientId; }
    public void setOauth2ClientId(String oauth2ClientId) { this.oauth2ClientId = oauth2ClientId; }
    public String getOauth2ClientSecret() { return oauth2ClientSecret; }
    public void setOauth2ClientSecret(String oauth2ClientSecret) { this.oauth2ClientSecret = oauth2ClientSecret; }
    public String getOauth2Username() { return oauth2Username; }
    public void setOauth2Username(String oauth2Username) { this.oauth2Username = oauth2Username; }
    public String getOauth2Password() { return oauth2Password; }
    public void setOauth2Password(String oauth2Password) { this.oauth2Password = oauth2Password; }
    public String getJsonLoginUrl() { return jsonLoginUrl; }
    public void setJsonLoginUrl(String jsonLoginUrl) { this.jsonLoginUrl = jsonLoginUrl; }
    public String getJsonLoginUsername() { return jsonLoginUsername; }
    public void setJsonLoginUsername(String jsonLoginUsername) { this.jsonLoginUsername = jsonLoginUsername; }
    public String getJsonLoginPassword() { return jsonLoginPassword; }
    public void setJsonLoginPassword(String jsonLoginPassword) { this.jsonLoginPassword = jsonLoginPassword; }
    public String getJsonLoginTokenPath() { return jsonLoginTokenPath; }
    public void setJsonLoginTokenPath(String jsonLoginTokenPath) { this.jsonLoginTokenPath = jsonLoginTokenPath; }
}
