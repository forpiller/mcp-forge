package it.forpiller.mcpforge.config;

import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.service.SourceRegistrationService;
import it.forpiller.mcpforge.service.SourceToolCacheService;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.Optional;

public class StartupSourceLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSourceLoader.class);

    private final SourceRegistrationHistoryService historyService;
    private final SourceRegistrationService sourceRegistrationService;
    private final SourceToolCacheService cacheService;

    public StartupSourceLoader(
            SourceRegistrationHistoryService historyService,
            SourceRegistrationService sourceRegistrationService,
            SourceToolCacheService cacheService) {
        this.historyService = historyService;
        this.sourceRegistrationService = sourceRegistrationService;
        this.cacheService = cacheService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SourceRegistrationInput> all = historyService.findAllInputs();
        if (all.isEmpty()) {
            log.info("Nessuna fonte nello storico — avvio senza tool pre-caricati");
            return;
        }
        log.info("Auto-reload: {} fonte/i trovate nello storico", all.size());
        int ok = 0;
        for (SourceRegistrationInput entry : all) {
            try {
                List<String> tools = loadEntry(entry);
                log.info("  ✓ '{}' — {} tool", entry.name(), tools.size());
                ok++;
            } catch (Exception e) {
                log.warn("  ✗ '{}' — fallito: {}", entry.name(), e.getMessage());
            }
        }
        log.info("Auto-reload completato: {}/{} fonti caricate", ok, all.size());
    }

    private List<String> loadEntry(SourceRegistrationInput entry) {
        Optional<List<OperationDescriptor>> cached = cacheService.findOperations(entry.name());
        if (cached.isPresent()) {
            log.debug("'{}' caricata da cache H2 (nessuna chiamata HTTP)", entry.name());
            return sourceRegistrationService.registerWithCachedOps(entry, cached.get());
        }
        log.debug("'{}' non in cache — re-fetch spec OpenAPI", entry.name());
        return sourceRegistrationService.register(entry);
    }
}
