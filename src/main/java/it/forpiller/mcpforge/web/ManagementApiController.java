package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.service.PortsService;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.service.SourceRegistrationService;
import it.forpiller.mcpforge.web.dto.ManagedServiceRequest;
import it.forpiller.mcpforge.web.dto.PortInfo;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management")
public class ManagementApiController {

    private final PortsService portsService;
    private final SourceRegistrationHistoryService historyService;
    private final SourceRegistrationService sourceRegistrationService;
    private final ToolRegistrar toolRegistrar;

    public ManagementApiController(
            PortsService portsService,
            SourceRegistrationHistoryService historyService,
            SourceRegistrationService sourceRegistrationService,
            ToolRegistrar toolRegistrar) {
        this.portsService = portsService;
        this.historyService = historyService;
        this.sourceRegistrationService = sourceRegistrationService;
        this.toolRegistrar = toolRegistrar;
    }

    @GetMapping("/services")
    public List<PortInfo> listServices() {
        return portsService.listAll();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addService(@RequestBody ManagedServiceRequest request) {
        if (request.name() == null || request.name().isBlank() ||
            request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and baseUrl are required");
        }
        historyService.upsertFromForm(request);
        return Map.of("success", true, "name", request.name());
    }

    @PostMapping("/services/{name}/start")
    public Map<String, Object> startService(@PathVariable String name) {
        String missing = portsService.missingConfig(name);
        if (missing != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing config: " + missing);
        }
        boolean ok = portsService.start(name);
        return Map.of("success", ok, "name", name);
    }

    @PostMapping("/services/{name}/stop")
    public Map<String, Object> stopService(@PathVariable String name) {
        boolean ok = portsService.kill(name);
        return Map.of("success", ok, "name", name);
    }

    @PostMapping("/services/{name}/restart")
    public Map<String, Object> restartService(@PathVariable String name) {
        String missing = portsService.missingConfig(name);
        if (missing != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing config: " + missing);
        }
        boolean ok = portsService.restart(name);
        return Map.of("success", ok, "name", name);
    }

    @GetMapping("/services/{name}/status")
    public Map<String, Object> serviceStatus(@PathVariable String name) {
        PortInfo info = portsService.getByName(name);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: " + name);
        }
        return Map.of(
            "name", info.sourceName(),
            "up", info.up(),
            "port", info.port(),
            "pid", info.pid() != null ? info.pid() : 0,
            "baseUrl", info.baseUrl()
        );
    }

    @DeleteMapping("/services/{name}")
    public Map<String, Object> removeService(@PathVariable String name) {
        if ("mcp-forge".equals(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove mcp-forge");
        }
        boolean ok = historyService.deleteByName(name);
        return Map.of("success", ok, "name", name);
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registerSource(@RequestBody RegisterSourceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il campo 'name' è obbligatorio");
        }
        if (request.openApiUrl() == null || request.openApiUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il campo 'openApiUrl' è obbligatorio");
        }
        SourceRegistrationInput input = new SourceRegistrationInput(
            request.name(), request.openApiUrl(), request.baseUrl(),
            null, null, null, null, null, null, null, null, null, null, null);
        List<String> tools = sourceRegistrationService.register(input);
        return Map.of("success", true, "name", input.name(), "toolCount", tools.size());
    }

    record RegisterSourceRequest(
        String name,
        @JsonProperty("openApiUrl") String openApiUrl,
        @JsonProperty("baseUrl") String baseUrl
    ) {}

    @DeleteMapping("/sources/{name}")
    public Map<String, Object> disconnectSource(@PathVariable String name) {
        List<String> removed = toolRegistrar.unregisterSource(name);
        return Map.of("success", true, "name", name, "toolsRemoved", removed.size());
    }
}
