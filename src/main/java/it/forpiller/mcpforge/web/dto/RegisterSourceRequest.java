package it.forpiller.mcpforge.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterSourceRequest(
        @NotBlank(message = "il nome della fonte è obbligatorio")
        String name,

        @NotBlank(message = "l'URL dello spec OpenAPI è obbligatorio")
        String openApiUrl,

        @NotBlank(message = "la base URL dell'API è obbligatoria")
        String baseUrl,

        String authHeaderName,

        String authHeaderValue
) {
}
