package it.forpiller.mcpforge.service;

import it.forpiller.mcpforge.cache.SourceToolCache;
import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.repository.SourceToolCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

public class SourceToolCacheService {

    private static final Logger log = LoggerFactory.getLogger(SourceToolCacheService.class);
    private static final TypeReference<List<OperationDescriptor>> OPERATIONS_TYPE = new TypeReference<>() {};

    private final SourceToolCacheRepository repository;
    private final ObjectMapper objectMapper;

    public SourceToolCacheService(SourceToolCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void save(String sourceName, List<OperationDescriptor> operations, String specHash) {
        try {
            String json = objectMapper.writeValueAsString(operations);
            SourceToolCache existing = repository.findById(sourceName).orElse(null);
            if (existing != null) {
                existing.update(json, specHash);
                repository.save(existing);
            } else {
                repository.save(new SourceToolCache(sourceName, json, specHash));
            }
            log.debug("Cache salvata per '{}': {} operazioni", sourceName, operations.size());
        } catch (Exception e) {
            log.warn("Impossibile salvare cache per '{}': {}", sourceName, e.getMessage());
        }
    }

    public Optional<String> findSpecHash(String sourceName) {
        return repository.findById(sourceName).map(SourceToolCache::getSpecHash);
    }

    public Optional<List<OperationDescriptor>> findOperations(String sourceName) {
        return repository.findById(sourceName).flatMap(cache -> {
            try {
                return Optional.of(objectMapper.readValue(cache.getOperationsJson(), OPERATIONS_TYPE));
            } catch (Exception e) {
                log.warn("Cache corrotta per '{}', verrà re-ingested: {}", sourceName, e.getMessage());
                repository.deleteById(sourceName);
                return Optional.empty();
            }
        });
    }

    public void delete(String sourceName) {
        repository.deleteById(sourceName);
    }
}
