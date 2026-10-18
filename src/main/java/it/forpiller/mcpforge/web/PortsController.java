package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.service.PortsService;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.web.dto.ManagedServiceRequest;
import it.forpiller.mcpforge.web.dto.PortInfo;
import it.forpiller.mcpforge.web.dto.ProcessConfigRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ports")
public class PortsController {

    private final PortsService portsService;
    private final SourceRegistrationHistoryService historyService;

    public PortsController(PortsService portsService, SourceRegistrationHistoryService historyService) {
        this.portsService = portsService;
        this.historyService = historyService;
    }

    @GetMapping
    public String ports(Model model) {
        model.addAttribute("ports", portsService.listAll());
        return "ports";
    }

    @GetMapping("/cards")
    public String cards(Model model) {
        model.addAttribute("ports", portsService.listAll());
        return "ports :: cardList";
    }

    @PostMapping("/{name}/kill")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> kill(@PathVariable String name) {
        boolean ok = portsService.kill(name);
        return Map.of("success", ok);
    }

    @PostMapping("/{name}/start")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> start(@PathVariable String name) {
        String missing = portsService.missingConfig(name);
        if (missing != null) {
            return Map.of("success", false, "needsConfig", true, "message", missing);
        }
        boolean ok = portsService.start(name);
        return Map.of("success", ok);
    }

    @PostMapping("/{name}/restart")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> restart(@PathVariable String name) {
        String missing = portsService.missingConfig(name);
        if (missing != null) {
            return Map.of("success", false, "needsConfig", true, "message", missing);
        }
        boolean ok = portsService.restart(name);
        return Map.of("success", ok);
    }

    @PostMapping("/{name}/save-config")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> saveConfig(
            @PathVariable String name,
            @RequestParam String startCommand,
            @RequestParam String workingDir,
            @RequestParam(required = false, defaultValue = "21") String jdkVersion,
            @RequestParam(required = false, defaultValue = "") String envVars) {
        historyService.saveProcessConfig(new ProcessConfigRequest(name, startCommand, workingDir, jdkVersion, envVars));
        return Map.of("success", true);
    }

    @PostMapping("/new")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> addService(
            @RequestParam String name,
            @RequestParam String baseUrl,
            @RequestParam(defaultValue = "") String startCommand,
            @RequestParam(defaultValue = "") String workingDir,
            @RequestParam(defaultValue = "21") String jdkVersion,
            @RequestParam(defaultValue = "") String envVars) {
        if (name.isBlank() || baseUrl.isBlank()) {
            return Map.of("success", false, "message", "Nome e URL base sono obbligatori");
        }
        historyService.upsertFromForm(
                new ManagedServiceRequest(name, baseUrl, startCommand, workingDir, jdkVersion, envVars));
        return Map.of("success", true);
    }

    @PostMapping("/{name}/open-terminal")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> openTerminal(@PathVariable String name) {
        boolean ok = portsService.openTerminal(name);
        return Map.of("success", ok);
    }

    @GetMapping("/pick-directory")
    @ResponseBody
    public Map<String, Object> pickDirectory() {
        String path = portsService.pickDirectory();
        if (path == null) return Map.of("cancelled", true);
        return Map.of("path", path);
    }

    @GetMapping("/jdk-versions")
    @ResponseBody
    public List<Map<String, String>> jdkVersions() {
        return portsService.listInstalledJdks().stream()
                .map(j -> Map.of("version", j.version(), "displayName", j.displayName()))
                .toList();
    }

    @PostMapping("/discover")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> discover() {
        int added = 0;
        for (String[] c : portsService.listDockerContainers()) {
            if (historyService.addManagedService(
                    new ManagedServiceRequest(c[0], c[1], "", "", "21", ""))) added++;
        }
        for (String[] p : portsService.scanLocalProcesses()) {
            if (historyService.addManagedService(
                    new ManagedServiceRequest(p[0], p[1], p[2], p[3], "21", ""))) added++;
        }
        int removed = portsService.cleanupDuplicates();
        return Map.of("success", true, "added", added, "removed", removed);
    }

    @PostMapping("/{name}/remove")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> removeService(@PathVariable String name) {
        if ("mcp-forge".equals(name)) return Map.of("success", false, "message", "Cannot remove mcp-forge");
        boolean ok = historyService.deleteByName(name);
        return Map.of("success", ok);
    }

    @GetMapping("/{name}/log")
    public String logPage(@PathVariable String name, Model model) {
        model.addAttribute("sourceName", name);
        model.addAttribute("logFile", portsService.logFilePath(name));
        model.addAttribute("lines", portsService.tailLog(name, 200));
        return "ports-log";
    }

    @GetMapping("/{name}/log/tail")
    @ResponseBody
    public Map<String, Object> logTail(
            @PathVariable String name,
            @RequestParam(defaultValue = "200") int lines) {
        List<String> logLines = portsService.tailLog(name, lines);
        return Map.of("lines", logLines, "exists", !logLines.isEmpty());
    }

    @GetMapping("/{name}/status")
    @ResponseBody
    public Map<String, Object> status(@PathVariable String name) {
        PortInfo info = portsService.getByName(name);
        if (info == null) return Map.of("up", false);
        return Map.of("up", info.up(), "pid", info.pid() != null ? info.pid() : 0, "port", info.port());
    }
}
