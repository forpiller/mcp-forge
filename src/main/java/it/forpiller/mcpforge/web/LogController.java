package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.service.AuditLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

@Controller
public class LogController {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50, 100);
    private static final int DEFAULT_SIZE = 10;

    private final AuditLogService auditLogService;

    public LogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/log")
    public String log(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "") String source,
            Model model) {
        int safeSize = ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;
        int safePage = Math.max(page, 0);

        var entries = source.isBlank()
                ? auditLogService.findAll(safePage, safeSize)
                : auditLogService.findBySourceName(source, safePage, safeSize);

        model.addAttribute("entries", entries);
        model.addAttribute("size", safeSize);
        model.addAttribute("selectedSource", source);
        model.addAttribute("sourceNames", auditLogService.findDistinctSourceNames());
        return "log";
    }
}
