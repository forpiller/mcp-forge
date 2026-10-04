package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * Pagina di sola lettura sull'audit log: ogni chiamata ai tool generati,
 * con esito, stato HTTP e durata. Paginato per non caricare tutto lo storico
 * in una volta sola.
 */
@Controller
public class LogController {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50, 100);
    private static final int DEFAULT_SIZE = 10;

    private final AuditLogRepository auditLogRepository;

    public LogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/log")
    public String log(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        int safeSize = ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;

        Page<?> entries = auditLogRepository.findAll(
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id")));

        model.addAttribute("entries", entries);
        model.addAttribute("size", safeSize);
        return "log";
    }
}
