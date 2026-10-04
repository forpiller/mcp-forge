package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.ingest.OpenApiIngestService;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.mcp.SourceConfig;
import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.web.dto.RegisterSourceRequest;
import it.forpiller.mcpforge.web.dto.RegisterSourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin endpoint: registra una fonte API (spec OpenAPI + base URL + auth)
 * e genera al volo i tool MCP corrispondenti, subito disponibili ai client
 * MCP connessi senza riavviare l'applicazione.
 */
@RestController
@RequestMapping("/api/sources")
public class SourceRegistrationController {

    private final OpenApiIngestService openApiIngestService;
    private final ToolRegistrar toolRegistrar;

    public SourceRegistrationController(OpenApiIngestService openApiIngestService, ToolRegistrar toolRegistrar) {
        this.openApiIngestService = openApiIngestService;
        this.toolRegistrar = toolRegistrar;
    }

    @PostMapping
    public ResponseEntity<RegisterSourceResponse> register(@Valid @RequestBody RegisterSourceRequest request) {
        List<OperationDescriptor> operations = openApiIngestService.ingest(request.openApiUrl());

        SourceConfig source = new SourceConfig(
                request.name(), request.baseUrl(), request.authHeaderName(), request.authHeaderValue());

        List<String> toolNames = toolRegistrar.registerSource(source, operations);

        return ResponseEntity.ok(new RegisterSourceResponse(request.name(), toolNames.size(), toolNames));
    }
}
