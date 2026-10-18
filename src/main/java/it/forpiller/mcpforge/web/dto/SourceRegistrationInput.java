package it.forpiller.mcpforge.web.dto;

public record SourceRegistrationInput(
        String name,
        String openApiUrl,
        String baseUrl,
        String authHeaderName,
        String authHeaderValue,
        String oauth2TokenUrl,
        String oauth2ClientId,
        String oauth2ClientSecret,
        String oauth2Username,
        String oauth2Password,
        String jsonLoginUrl,
        String jsonLoginUsername,
        String jsonLoginPassword,
        String jsonLoginTokenPath
) {
    public boolean hasOAuth2() {
        return oauth2TokenUrl != null && !oauth2TokenUrl.isBlank();
    }

    public boolean hasJsonLogin() {
        return jsonLoginUrl != null && !jsonLoginUrl.isBlank();
    }
}
