package it.forpiller.mcpforge.web.dto;

public class UpdateSourceForm {

    private String openApiUrl = "";
    private String baseUrl = "";
    private String oauth2TokenUrl = "";
    private String oauth2ClientId = "";
    private String oauth2ClientSecret = "";
    private String oauth2Username = "";
    private String oauth2Password = "";
    private String authHeaderName = "";
    private String authHeaderValue = "";

    public String getOpenApiUrl() { return openApiUrl; }
    public void setOpenApiUrl(String openApiUrl) { this.openApiUrl = openApiUrl; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

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

    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }

    public String getAuthHeaderValue() { return authHeaderValue; }
    public void setAuthHeaderValue(String authHeaderValue) { this.authHeaderValue = authHeaderValue; }
}
