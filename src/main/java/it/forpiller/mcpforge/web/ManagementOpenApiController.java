package it.forpiller.mcpforge.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ManagementOpenApiController {

    @GetMapping(value = "/api/management/openapi.json", produces = "application/json")
    public Map<String, Object> openApiSpec() {
        return Map.of(
            "openapi", "3.0.3",
            "info", Map.of(
                "title", "MCP-Forge Management API",
                "description", "Manage services and MCP sources in mcp-forge",
                "version", "1.0.0"
            ),
            "servers", java.util.List.of(Map.of("url", "http://localhost:8090")),
            "paths", buildPaths()
        );
    }

    private Map<String, Object> buildPaths() {
        return Map.ofEntries(
            Map.entry("/api/management/services", Map.of(
                "get", op("listServices", "List all managed services with status", java.util.List.of()),
                "post", opWithBody("addService", "Add a new managed service",
                    Map.of(
                        "name", Map.of("type", "string", "description", "Unique service name"),
                        "baseUrl", Map.of("type", "string", "description", "Base URL of the service"),
                        "startCommand", Map.of("type", "string", "description", "Maven/shell command to start the service"),
                        "workingDir", Map.of("type", "string", "description", "Working directory"),
                        "jdkVersion", Map.of("type", "string", "description", "JDK version (e.g. 21)"),
                        "envVars", Map.of("type", "string", "description", "Environment variables (KEY=val;KEY2=val2)")
                    ),
                    java.util.List.of("name", "baseUrl"))
            )),
            Map.entry("/api/management/services/{name}/start", Map.of(
                "post", opWithPath("startService", "Start a managed service", "name", "Service name")
            )),
            Map.entry("/api/management/services/{name}/stop", Map.of(
                "post", opWithPath("stopService", "Stop (kill) a managed service", "name", "Service name")
            )),
            Map.entry("/api/management/services/{name}/restart", Map.of(
                "post", opWithPath("restartService", "Restart a managed service", "name", "Service name")
            )),
            Map.entry("/api/management/services/{name}/status", Map.of(
                "get", opWithPath("getServiceStatus", "Get status of a managed service", "name", "Service name")
            )),
            Map.entry("/api/management/services/{name}", Map.of(
                "delete", opWithPath("removeService", "Remove a managed service", "name", "Service name")
            )),
            Map.entry("/api/management/sources", Map.of(
                "post", opWithBody("registerSource", "Register a new OpenAPI source and generate MCP tools",
                    Map.of(
                        "name", Map.of("type", "string", "description", "Unique source name"),
                        "openApiUrl", Map.of("type", "string", "description", "URL of the OpenAPI spec"),
                        "baseUrl", Map.of("type", "string", "description", "Base URL of the API")
                    ),
                    java.util.List.of("name", "openApiUrl", "baseUrl"))
            )),
            Map.entry("/api/management/sources/{name}", Map.of(
                "delete", opWithPath("disconnectSource", "Disconnect an MCP source and remove its tools", "name", "Source name")
            ))
        );
    }

    private Map<String, Object> op(String operationId, String summary, java.util.List<Map<String, Object>> params) {
        return Map.of(
            "operationId", operationId,
            "summary", summary,
            "parameters", params,
            "responses", Map.of("200", Map.of("description", "Success"))
        );
    }

    private Map<String, Object> opWithPath(String operationId, String summary, String paramName, String paramDesc) {
        return Map.of(
            "operationId", operationId,
            "summary", summary,
            "parameters", java.util.List.of(Map.of(
                "name", paramName,
                "in", "path",
                "required", true,
                "schema", Map.of("type", "string"),
                "description", paramDesc
            )),
            "responses", Map.of("200", Map.of("description", "Success"))
        );
    }

    private Map<String, Object> opWithBody(String operationId, String summary,
                                            Map<String, Object> properties,
                                            java.util.List<String> required) {
        return Map.of(
            "operationId", operationId,
            "summary", summary,
            "parameters", java.util.List.of(),
            "requestBody", Map.of(
                "required", true,
                "content", Map.of(
                    "application/json", Map.of(
                        "schema", Map.of(
                            "type", "object",
                            "properties", properties,
                            "required", required
                        )
                    )
                )
            ),
            "responses", Map.of("200", Map.of("description", "Success"), "201", Map.of("description", "Created"))
        );
    }
}
