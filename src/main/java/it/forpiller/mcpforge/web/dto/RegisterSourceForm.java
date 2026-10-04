package it.forpiller.mcpforge.web.dto;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOpenApiUrl() {
        return openApiUrl;
    }

    public void setOpenApiUrl(String openApiUrl) {
        this.openApiUrl = openApiUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthHeaderValue() {
        return authHeaderValue;
    }

    public void setAuthHeaderValue(String authHeaderValue) {
        this.authHeaderValue = authHeaderValue;
    }
}
