package it.forpiller.mcpforge.mcp;

public record JsonLoginConfig(
        String loginUrl,
        String username,
        String password,
        String tokenJsonPath
) {
    public boolean isConfigured() {
        return loginUrl != null && !loginUrl.isBlank() && username != null && !username.isBlank();
    }

    public String effectiveTokenPath() {
        return (tokenJsonPath != null && !tokenJsonPath.isBlank()) ? tokenJsonPath : "access_token";
    }
}
