package it.forpiller.mcpforge.config;

import it.forpiller.mcpforge.mcp.SourceRegistry;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.service.SourceRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Set;
import java.util.stream.Collectors;

public class SpecChangeWatcherService {

    private static final Logger log = LoggerFactory.getLogger(SpecChangeWatcherService.class);

    private final SourceRegistry sourceRegistry;
    private final SourceRegistrationHistoryService historyService;
    private final SourceRegistrationService sourceRegistrationService;
    private final McpForgeProperties properties;

    public SpecChangeWatcherService(
            SourceRegistry sourceRegistry,
            SourceRegistrationHistoryService historyService,
            SourceRegistrationService sourceRegistrationService,
            McpForgeProperties properties) {
        this.sourceRegistry = sourceRegistry;
        this.historyService = historyService;
        this.sourceRegistrationService = sourceRegistrationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${mcp-forge.spec-watcher.interval-ms:60000}")
    public void checkForChanges() {
        if (!properties.getSpecWatcher().isEnabled()) {
            return;
        }

        Set<String> activeSources = sourceRegistry.all().stream()
                .map(t -> t.source().name())
                .collect(Collectors.toSet());

        if (activeSources.isEmpty()) {
            return;
        }

        log.info("SpecWatcher: controllo {} fonte/i attive", activeSources.size());

        for (String sourceName : activeSources) {
            try {
                historyService.findInputByName(sourceName).ifPresent(input -> {
                    boolean changed = sourceRegistrationService.refreshIfChanged(input);
                    if (changed) {
                        log.info("SpecWatcher: spec aggiornata per '{}' — tool MCP ricaricati", sourceName);
                    }
                });
            } catch (Exception e) {
                log.warn("SpecWatcher: impossibile raggiungere spec di '{}': {}", sourceName, e.getMessage());
            }
        }
    }
}
