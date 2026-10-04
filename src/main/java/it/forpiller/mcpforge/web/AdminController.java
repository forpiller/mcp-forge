package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.history.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.ingest.OpenApiIngestService;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.mcp.RegisteredTool;
import it.forpiller.mcpforge.mcp.SourceConfig;
import it.forpiller.mcpforge.mcp.SourceRegistry;
import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.web.dto.RegisterSourceForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pagina admin minimale: registrare una fonte API e vedere i tool MCP
 * generati per ciascuna. È solo per chi configura mcp-forge (uso occasionale);
 * non ha nulla a che fare con come un'AI usa i tool, che resta il protocollo MCP.
 */
@Controller
public class AdminController {

    private final SourceRegistry sourceRegistry;
    private final OpenApiIngestService openApiIngestService;
    private final ToolRegistrar toolRegistrar;
    private final SourceRegistrationHistoryService historyService;

    public AdminController(SourceRegistry sourceRegistry, OpenApiIngestService openApiIngestService,
                            ToolRegistrar toolRegistrar, SourceRegistrationHistoryService historyService) {
        this.sourceRegistry = sourceRegistry;
        this.openApiIngestService = openApiIngestService;
        this.toolRegistrar = toolRegistrar;
        this.historyService = historyService;
    }

    @GetMapping("/")
    public String home(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterSourceForm());
        }
        model.addAttribute("sourceGroups", groupBySource());
        model.addAttribute("lastRegistration", historyService.findLast().orElse(null));
        return "admin";
    }

    @GetMapping("/storico")
    public String storico(Model model) {
        model.addAttribute("history", historyService.findAll());
        return "storico";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterSourceForm form, BindingResult bindingResult,
                            Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            if (historyService.existsByName(form.getName())) {
                bindingResult.rejectValue("name", "duplicate", "già presente");
            }
            if (historyService.existsByOpenApiUrl(form.getOpenApiUrl())) {
                bindingResult.rejectValue("openApiUrl", "duplicate", "già presente");
            }
            if (historyService.existsByBaseUrl(form.getBaseUrl())) {
                bindingResult.rejectValue("baseUrl", "duplicate", "già presente");
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("sourceGroups", groupBySource());
            model.addAttribute("lastRegistration", historyService.findLast().orElse(null));
            return "admin";
        }

        doRegister(form.getName(), form.getOpenApiUrl(), form.getBaseUrl(),
                form.getAuthHeaderName(), form.getAuthHeaderValue(), redirectAttributes);
        return "redirect:/";
    }

    /**
     * Usato dal bottone "Reinserisci": ripropone una registrazione già presente
     * nello storico, quindi NON applica il controllo duplicati di /register
     * (sarebbe sempre e comunque un duplicato, per definizione).
     */
    @PostMapping("/replay")
    public String replay(@RequestParam String name, @RequestParam String openApiUrl, @RequestParam String baseUrl,
                          @RequestParam(required = false) String authHeaderName,
                          @RequestParam(required = false) String authHeaderValue,
                          RedirectAttributes redirectAttributes) {
        doRegister(name, openApiUrl, baseUrl, authHeaderName, authHeaderValue, redirectAttributes);
        return "redirect:/";
    }

    private void doRegister(String name, String openApiUrl, String baseUrl, String authHeaderName,
                             String authHeaderValue, RedirectAttributes redirectAttributes) {
        try {
            List<OperationDescriptor> operations = openApiIngestService.ingest(openApiUrl);
            SourceConfig source = new SourceConfig(name, baseUrl, authHeaderName, authHeaderValue);
            List<String> toolNames = toolRegistrar.registerSource(source, operations);
            historyService.saveOrUpdate(name, openApiUrl, baseUrl, authHeaderName, authHeaderValue);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Fonte \"" + name + "\" registrata: " + toolNames.size() + " tool generati.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Errore nella registrazione: " + e.getMessage());
        }
    }

    private Map<String, List<RegisteredTool>> groupBySource() {
        return sourceRegistry.all().stream()
                .collect(Collectors.groupingBy(t -> t.source().name(), LinkedHashMap::new, Collectors.toList()));
    }
}
