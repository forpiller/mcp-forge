package it.forpiller.mcpforge.web.dto;

public record PortInfo(
        String sourceName,
        String baseUrl,
        int port,
        boolean up,
        Long pid,
        String detectedWorkingDir,
        String startCommand,
        String savedWorkingDir,
        String jdkVersion,
        String envVars,
        boolean dockerManaged,
        String dockerContainerName,
        String gitBranch
) {
}
