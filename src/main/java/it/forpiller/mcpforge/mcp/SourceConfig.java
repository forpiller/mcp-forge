package it.forpiller.mcpforge.mcp;

public record SourceConfig(
        String name,
        String baseUrl,
        String authHeaderName,
        String authHeaderValue,
        OAuth2PasswordGrantConfig oauth2,
        JsonLoginConfig jsonLogin
) {
}
