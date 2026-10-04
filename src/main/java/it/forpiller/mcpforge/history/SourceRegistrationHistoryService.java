package it.forpiller.mcpforge.history;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SourceRegistrationHistoryService {

    private final SourceRegistrationHistoryRepository repository;

    public SourceRegistrationHistoryService(SourceRegistrationHistoryRepository repository) {
        this.repository = repository;
    }

    public void saveOrUpdate(String name, String openApiUrl, String baseUrl,
                              String authHeaderName, String authHeaderValue) {
        SourceRegistrationHistory entry = repository.findByName(name)
                .orElseGet(() -> new SourceRegistrationHistory(name, openApiUrl, baseUrl, authHeaderName, authHeaderValue));
        entry.updateFrom(openApiUrl, baseUrl, authHeaderName, authHeaderValue);
        repository.save(entry);
    }

    public List<SourceRegistrationHistory> findAll() {
        return repository.findAllByOrderByLastUsedAtDesc();
    }

    public Optional<SourceRegistrationHistory> findLast() {
        return repository.findFirstByOrderByLastUsedAtDesc();
    }

    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

    public boolean existsByOpenApiUrl(String openApiUrl) {
        return repository.existsByOpenApiUrl(openApiUrl);
    }

    public boolean existsByBaseUrl(String baseUrl) {
        return repository.existsByBaseUrl(baseUrl);
    }
}
