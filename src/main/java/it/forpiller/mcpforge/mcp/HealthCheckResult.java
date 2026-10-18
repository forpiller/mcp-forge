package it.forpiller.mcpforge.mcp;

public record HealthCheckResult(String endpoint, int statusCode, boolean ok, String error) {}
