package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.mcp.JsonLoginConfig;
import it.forpiller.mcpforge.mcp.JsonLoginTokenService;
import it.forpiller.mcpforge.mcp.OAuth2PasswordGrantConfig;
import it.forpiller.mcpforge.mcp.OAuth2TokenService;
import it.forpiller.mcpforge.mcp.HealthCheckResult;
import it.forpiller.mcpforge.mcp.RegisteredTool;
import it.forpiller.mcpforge.mcp.SourceHealthCheckService;
import it.forpiller.mcpforge.mcp.SourceRegistry;
import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.service.AuditLogService;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.service.SourceRegistrationService;
import it.forpiller.mcpforge.web.dto.RegisterSourceForm;
import it.forpiller.mcpforge.web.dto.RegisterStagingForm;
import it.forpiller.mcpforge.web.dto.SourceRegistrationHistoryDto;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
import it.forpiller.mcpforge.web.dto.TokenDto;
import it.forpiller.mcpforge.web.dto.UpdateSourceForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private static final String SESSION_PENDING_PREFIX = "pending_reg_";

    private final SourceRegistry sourceRegistry;
    private final ToolRegistrar toolRegistrar;
    private final SourceRegistrationHistoryService historyService;
    private final OAuth2TokenService oAuth2TokenService;
    private final JsonLoginTokenService jsonLoginTokenService;
    private final AuditLogService auditLogService;
    private final SourceRegistrationService sourceRegistrationService;
    private final SourceHealthCheckService healthCheckService;

    public AdminController(
            SourceRegistry sourceRegistry,
            ToolRegistrar toolRegistrar,
            SourceRegistrationHistoryService historyService,
            OAuth2TokenService oAuth2TokenService,
            JsonLoginTokenService jsonLoginTokenService,
            AuditLogService auditLogService,
            SourceRegistrationService sourceRegistrationService,
            SourceHealthCheckService healthCheckService) {
        this.sourceRegistry = sourceRegistry;
        this.toolRegistrar = toolRegistrar;
        this.historyService = historyService;
        this.oAuth2TokenService = oAuth2TokenService;
        this.jsonLoginTokenService = jsonLoginTokenService;
        this.auditLogService = auditLogService;
        this.sourceRegistrationService = sourceRegistrationService;
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/")
    public String home(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterSourceForm());
        }
        Map<String, List<RegisteredTool>> sourceGroups = groupBySource();
        model.addAttribute("sourceGroups", sourceGroups);
        model.addAttribute("sourceStatus", sourceStatus(sourceGroups));
        model.addAttribute("lastRegistration", historyService.findLast().orElse(null));
        return "admin";
    }

    @GetMapping("/fonti")
    public String storico(Model model) {
        Set<String> activeSources = sourceRegistry.all().stream()
                .map(t -> t.source().name())
                .collect(Collectors.toSet());

        List<SourceRegistrationHistoryDto> history = historyService.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(h -> activeSources.contains(h.getName()) ? 0 : 1))
                .collect(Collectors.toList());

        model.addAttribute("history", history);
        model.addAttribute("activeSources", activeSources);
        return "storico";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("form") RegisterSourceForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session) {
        if (bindingResult.hasErrors()) {
            Map<String, List<RegisteredTool>> sourceGroups = groupBySource();
            model.addAttribute("sourceGroups", sourceGroups);
            model.addAttribute("sourceStatus", sourceStatus(sourceGroups));
            model.addAttribute("lastRegistration", historyService.findLast().orElse(null));
            return "admin";
        }

        boolean duplicate = historyService.existsByName(form.getName())
                || historyService.existsByOpenApiUrl(form.getOpenApiUrl())
                || historyService.existsByBaseUrl(form.getBaseUrl());

        if (duplicate) {
            String pendingKey = UUID.randomUUID().toString();
            session.setAttribute(SESSION_PENDING_PREFIX + pendingKey, form.toInput());
            redirectAttributes.addFlashAttribute("duplicatePendingKey", pendingKey);
            redirectAttributes.addFlashAttribute("duplicateName", form.getName());
            redirectAttributes.addFlashAttribute("duplicateNewName", form.getName());
            redirectAttributes.addFlashAttribute("duplicateNewBaseUrl", form.getBaseUrl());
            redirectAttributes.addFlashAttribute("duplicateNewOpenApiUrl", form.getOpenApiUrl());
            historyService.findConflicting(form.getName(), form.getOpenApiUrl(), form.getBaseUrl())
                    .ifPresent(existing -> {
                        redirectAttributes.addFlashAttribute("duplicateExistingName", existing.getName());
                        redirectAttributes.addFlashAttribute("duplicateExistingBaseUrl", existing.getBaseUrl());
                        redirectAttributes.addFlashAttribute("duplicateExistingOpenApiUrl", existing.getOpenApiUrl());
                        boolean active = sourceRegistry.all().stream()
                                .anyMatch(t -> t.source().name().equals(existing.getName()));
                        redirectAttributes.addFlashAttribute("duplicateExistingIsActive", active);
                    });
            return "redirect:/";
        }

        doRegisterInternal(form.toInput(), redirectAttributes);
        return "redirect:/";
    }

    @PostMapping("/replay-pending")
    public String replayPending(
            @ModelAttribute("pendingKey") String pendingKey,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        SourceRegistrationInput input = (SourceRegistrationInput) session.getAttribute(
                SESSION_PENDING_PREFIX + pendingKey);
        session.removeAttribute(SESSION_PENDING_PREFIX + pendingKey);
        if (input == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sessione scaduta o chiave non valida. Ripeti la registrazione.");
            return "redirect:/";
        }
        doRegisterInternal(input, redirectAttributes);
        return "redirect:/";
    }

    @PostMapping("/fonti/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        historyService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Registrazione eliminata dallo storico.");
        return "redirect:/fonti";
    }

    @PostMapping("/fonti/{id}/reinserisci")
    public String reinserisci(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        SourceRegistrationInput input = historyService.findInputById(id).orElse(null);
        if (input == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Registrazione non trovata.");
            return "redirect:/fonti";
        }
        doRegisterInternal(input, redirectAttributes);
        return "redirect:/";
    }

    @PostMapping("/fonti/{id}/update-and-reinserisci")
    public String updateAndReinserisci(
            @PathVariable Long id,
            @ModelAttribute UpdateSourceForm form,
            RedirectAttributes redirectAttributes) {
        SourceRegistrationInput base = historyService.findInputById(id).orElse(null);
        if (base == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Registrazione non trovata.");
            return "redirect:/fonti";
        }

        SourceRegistrationInput input = new SourceRegistrationInput(
                base.name(),
                form.getOpenApiUrl().isBlank() ? base.openApiUrl() : form.getOpenApiUrl(),
                form.getBaseUrl().isBlank() ? base.baseUrl() : form.getBaseUrl(),
                form.getAuthHeaderName().isBlank() ? base.authHeaderName() : form.getAuthHeaderName(),
                form.getAuthHeaderValue().isBlank() ? base.authHeaderValue() : form.getAuthHeaderValue(),
                form.getOauth2TokenUrl().isBlank() ? base.oauth2TokenUrl() : form.getOauth2TokenUrl(),
                form.getOauth2ClientId().isBlank() ? base.oauth2ClientId() : form.getOauth2ClientId(),
                form.getOauth2ClientSecret().isBlank() ? base.oauth2ClientSecret() : form.getOauth2ClientSecret(),
                form.getOauth2Username().isBlank() ? base.oauth2Username() : form.getOauth2Username(),
                form.getOauth2Password().isBlank() ? base.oauth2Password() : form.getOauth2Password(),
                base.jsonLoginUrl(), base.jsonLoginUsername(), base.jsonLoginPassword(), base.jsonLoginTokenPath());

        doRegisterInternal(input, redirectAttributes);
        return "redirect:/";
    }

    @PostMapping("/fonti/{id}/refresh-token")
    public String refreshToken(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        SourceRegistrationInput input = historyService.findInputById(id).orElse(null);
        if (input == null || (!input.hasOAuth2() && !input.hasJsonLogin())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Fonte non trovata o non configurata con auth automatica.");
            return "redirect:/fonti";
        }

        try {
            if (input.hasOAuth2()) {
                OAuth2PasswordGrantConfig oauth2 = new OAuth2PasswordGrantConfig(input.oauth2TokenUrl(),
                        input.oauth2ClientId(), input.oauth2ClientSecret(),
                        input.oauth2Username(), input.oauth2Password());
                oAuth2TokenService.invalidate(input.name());
                oAuth2TokenService.getAccessToken(input.name(), oauth2);
            } else {
                JsonLoginConfig jsonLogin = new JsonLoginConfig(input.jsonLoginUrl(),
                        input.jsonLoginUsername(), input.jsonLoginPassword(), input.jsonLoginTokenPath());
                jsonLoginTokenService.invalidate(input.name());
                jsonLoginTokenService.getAccessToken(input.name(), jsonLogin);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Token di \"" + input.name() + "\" aggiornato.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Login fallito per \"" + input.name() + "\": " + e.getMessage());
        }
        return "redirect:/fonti";
    }

    @PostMapping("/fonti/{id}/token")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TokenDto token(@PathVariable Long id) {
        SourceRegistrationInput input = historyService.findInputById(id).orElse(null);
        if (input == null || (!input.hasOAuth2() && !input.hasJsonLogin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fonte non trovata o non configurata con auth automatica.");
        }

        try {
            String accessToken;
            if (input.hasOAuth2()) {
                OAuth2PasswordGrantConfig oauth2 = new OAuth2PasswordGrantConfig(input.oauth2TokenUrl(),
                        input.oauth2ClientId(), input.oauth2ClientSecret(),
                        input.oauth2Username(), input.oauth2Password());
                accessToken = oAuth2TokenService.getAccessToken(input.name(), oauth2);
            } else {
                JsonLoginConfig jsonLogin = new JsonLoginConfig(input.jsonLoginUrl(),
                        input.jsonLoginUsername(), input.jsonLoginPassword(), input.jsonLoginTokenPath());
                accessToken = jsonLoginTokenService.getAccessToken(input.name(), jsonLogin);
            }
            return new TokenDto(accessToken);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Login fallito: " + e.getMessage());
        }
    }

    @PostMapping("/register-staging")
    public String registerStaging(
            @ModelAttribute RegisterStagingForm form,
            RedirectAttributes redirectAttributes) {
        String base = form.getStagingBaseUrl().endsWith("/")
                ? form.getStagingBaseUrl().substring(0, form.getStagingBaseUrl().length() - 1)
                : form.getStagingBaseUrl();
        String name = form.getStagingName().isBlank()
                ? base.replaceAll("^https?://", "").split("\\.")[0]
                : form.getStagingName();
        String openApiUrl = form.getStagingOpenApiUrl().isBlank()
                ? base + "/v3/api-docs"
                : (form.getStagingOpenApiUrl().startsWith("http")
                        ? form.getStagingOpenApiUrl() : base + form.getStagingOpenApiUrl());
        String loginUrl = form.getStagingLoginUrl().isBlank()
                ? base + "/api/login"
                : (form.getStagingLoginUrl().startsWith("http")
                        ? form.getStagingLoginUrl() : base + form.getStagingLoginUrl());

        SourceRegistrationInput input = new SourceRegistrationInput(
                name, openApiUrl, base,
                null, null, null, null, null, null, null,
                loginUrl, form.getStagingUsername(), form.getStagingPassword(), "access_token");
        doRegisterInternal(input, redirectAttributes);
        return "redirect:/";
    }

    @PostMapping("/sources/{sourceName}/disconnect")
    public String disconnect(@PathVariable String sourceName, RedirectAttributes redirectAttributes) {
        List<String> removed = toolRegistrar.unregisterSource(sourceName);
        if (removed.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Fonte \"" + sourceName + "\" non trovata o già disconnessa.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Fonte \"" + sourceName + "\" disconnessa: " + removed.size() + " tool rimossi.");
        }
        return "redirect:/";
    }

    @PostMapping("/sources/{sourceName}/disconnect-ajax")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> disconnectAjax(@PathVariable String sourceName) {
        List<String> removed = toolRegistrar.unregisterSource(sourceName);
        return Map.of("success", true, "removed", removed.size());
    }

    @PostMapping("/sources/{sourceName}/reconnect")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> reconnect(@PathVariable String sourceName) {
        SourceRegistrationInput input = historyService.findInputByName(sourceName).orElse(null);
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Fonte \"" + sourceName + "\" non trovata nello storico.");
        }
        List<String> toolNames = sourceRegistrationService.register(input);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("toolCount", toolNames.size());

        healthCheckService.check(sourceName).ifPresent(hc -> response.put("healthCheck",
                Map.of("endpoint", hc.endpoint(), "statusCode", hc.statusCode(), "ok", hc.ok())));

        return response;
    }

    private void doRegisterInternal(SourceRegistrationInput input, RedirectAttributes redirectAttributes) {
        try {
            List<String> toolNames = sourceRegistrationService.register(input);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Fonte \"" + input.name() + "\" registrata: " + toolNames.size() + " tool generati.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Errore nella registrazione: " + e.getMessage());
        }
    }

    private Map<String, List<RegisteredTool>> groupBySource() {
        return sourceRegistry.all().stream()
                .collect(Collectors.groupingBy(t -> t.source().name(), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, Boolean> sourceStatus(Map<String, List<RegisteredTool>> sourceGroups) {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (Map.Entry<String, List<RegisteredTool>> entry : sourceGroups.entrySet()) {
            String name = entry.getKey();
            Instant registeredAt = entry.getValue().isEmpty()
                    ? Instant.EPOCH : entry.getValue().get(0).registeredAt();
            status.put(name, auditLogService.isSourceHealthy(name, registeredAt));
        }
        return status;
    }
}
