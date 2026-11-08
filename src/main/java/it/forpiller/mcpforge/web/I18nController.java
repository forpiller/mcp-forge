package it.forpiller.mcpforge.web;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/i18n")
public class I18nController {

    private final MessageSource messageSource;

    public I18nController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping("/ports")
    public Map<String, String> ports(Locale locale) {
        return Map.ofEntries(
            Map.entry("restart", msg("ports.btn.restart", locale)),
            Map.entry("start", msg("ports.btn.start", locale)),
            Map.entry("restartStarted", msg("ports.msg.restartStarted", locale)),
            Map.entry("started", msg("ports.msg.started", locale)),
            Map.entry("stopped", msg("ports.msg.stopped", locale)),
            Map.entry("saved", msg("ports.msg.saved", locale)),
            Map.entry("error", msg("ports.msg.error", locale)),
            Map.entry("networkError", msg("ports.msg.networkError", locale)),
            Map.entry("configureCommand", msg("ports.msg.configureCommand", locale)),
            Map.entry("nameRequired", msg("ports.msg.nameRequired", locale)),
            Map.entry("discoverAdded", msg("ports.msg.discoverAdded", locale)),
            Map.entry("discoverNone", msg("ports.msg.discoverNone", locale)),
            Map.entry("discoverSearching", msg("ports.msg.discoverSearching", locale)),
            Map.entry("discoverLabel", msg("ports.btn.discover.label", locale)),
            Map.entry("removeConfirm", msg("ports.msg.removeConfirm", locale)),
            Map.entry("removed", msg("ports.msg.removed", locale))
        );
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
}
