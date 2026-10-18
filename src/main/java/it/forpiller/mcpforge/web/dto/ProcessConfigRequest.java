package it.forpiller.mcpforge.web.dto;

public record ProcessConfigRequest(
        String name,
        String startCommand,
        String workingDir,
        String jdkVersion,
        String envVars
) {
}
