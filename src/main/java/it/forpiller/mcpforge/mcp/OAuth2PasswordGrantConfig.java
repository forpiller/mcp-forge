package it.forpiller.mcpforge.mcp;

/**
 * Credenziali per il grant OAuth2 "password" (Spring Security OAuth / UAA):
 * quando presente su una {@link SourceConfig}, l'{@link ApiInvoker} fa login
 * da solo e rinnova il token in automatico — nessun bearer token da incollare
 * a mano quando scade.
 */
public record OAuth2PasswordGrantConfig(
        String tokenUrl,
        String clientId,
        String clientSecret,
        String username,
        String password
) {
    public boolean isConfigured() {
        return tokenUrl != null && !tokenUrl.isBlank()
                && username != null && !username.isBlank();
    }
}
