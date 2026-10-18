package it.forpiller.mcpforge.web.dto;

public record ManagedServiceRequest(
        String name,
        String baseUrl,
        String startCommand,
        String workingDir,
        String jdkVersion,
        String envVars
) {
}
