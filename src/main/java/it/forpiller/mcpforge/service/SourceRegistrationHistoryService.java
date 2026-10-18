package it.forpiller.mcpforge.service;

import it.forpiller.mcpforge.history.SourceRegistrationHistory;
import it.forpiller.mcpforge.repository.SourceRegistrationHistoryRepository;
import it.forpiller.mcpforge.web.dto.ManagedServiceRequest;
import it.forpiller.mcpforge.web.dto.ProcessConfigRequest;
import it.forpiller.mcpforge.web.dto.SourceRegistrationHistoryDto;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;

import java.util.List;
import java.util.Optional;

public class SourceRegistrationHistoryService {

    private final SourceRegistrationHistoryRepository repository;

    public SourceRegistrationHistoryService(SourceRegistrationHistoryRepository repository) {
        this.repository = repository;
    }

    public void saveOrUpdate(SourceRegistrationInput input) {
        SourceRegistrationHistory entry = repository.findByName(input.name())
                .orElseGet(() -> new SourceRegistrationHistory(input));
        entry.updateFrom(input);
        repository.save(entry);
    }

    public List<SourceRegistrationHistoryDto> findAll() {
        return repository.findAllByOrderByLastUsedAtDesc().stream()
                .map(SourceRegistrationHistoryDto::new)
                .toList();
    }

    public Optional<SourceRegistrationHistoryDto> findLast() {
        return repository.findFirstByOrderByLastUsedAtDesc()
                .map(SourceRegistrationHistoryDto::new);
    }

    public List<SourceRegistrationInput> findAllInputs() {
        return repository.findAllByOrderByLastUsedAtDesc().stream()
                .map(SourceRegistrationHistory::toInput)
                .toList();
    }

    public Optional<SourceRegistrationInput> findInputByName(String name) {
        return repository.findByName(name).map(SourceRegistrationHistory::toInput);
    }

    public Optional<SourceRegistrationInput> findInputById(Long id) {
        return repository.findById(id).map(SourceRegistrationHistory::toInput);
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

    public Optional<SourceRegistrationHistoryDto> findConflicting(
            String name,
            String openApiUrl,
            String baseUrl) {
        return repository.findFirstByNameOrOpenApiUrlOrBaseUrl(name, openApiUrl, baseUrl)
                .map(SourceRegistrationHistoryDto::new);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public boolean deleteByName(String name) {
        return repository.findByName(name).map(entry -> {
            repository.delete(entry);
            return true;
        }).orElse(false);
    }

    public void saveProcessConfig(ProcessConfigRequest request) {
        repository.findByName(request.name()).ifPresent(entry -> {
            entry.setStartCommand(request.startCommand());
            entry.setWorkingDir(request.workingDir());
            entry.setJdkVersion(request.jdkVersion());
            entry.setEnvVars(request.envVars());
            repository.save(entry);
        });
    }

    public boolean addManagedService(ManagedServiceRequest request) {
        if (repository.existsByName(request.name())) return false;
        repository.save(buildEntry(request));
        return true;
    }

    public void upsertFromForm(ManagedServiceRequest request) {
        SourceRegistrationHistory entry = repository.findByName(request.name())
                .orElseGet(SourceRegistrationHistory::new);
        entry.setName(request.name());
        entry.setBaseUrl(request.baseUrl());
        if (entry.getOpenApiUrl() == null) entry.setOpenApiUrl("");
        entry.setStartCommand(request.startCommand());
        entry.setWorkingDir(request.workingDir());
        entry.setJdkVersion(
                request.jdkVersion() != null && !request.jdkVersion().isBlank() ? request.jdkVersion() : "21");
        entry.setEnvVars(request.envVars());
        entry.setLastUsedAt(java.time.Instant.now());
        repository.save(entry);
    }

    private SourceRegistrationHistory buildEntry(ManagedServiceRequest request) {
        SourceRegistrationHistory entry = new SourceRegistrationHistory();
        entry.setName(request.name());
        entry.setBaseUrl(request.baseUrl());
        entry.setOpenApiUrl("");
        entry.setStartCommand(request.startCommand());
        entry.setWorkingDir(request.workingDir());
        entry.setJdkVersion(
                request.jdkVersion() != null && !request.jdkVersion().isBlank() ? request.jdkVersion() : "21");
        entry.setEnvVars(request.envVars());
        entry.setLastUsedAt(java.time.Instant.now());
        return entry;
    }
}
