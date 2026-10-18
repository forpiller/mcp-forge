package it.forpiller.mcpforge.repository;

import it.forpiller.mcpforge.history.SourceRegistrationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SourceRegistrationHistoryRepository extends JpaRepository<SourceRegistrationHistory, Long> {

    Optional<SourceRegistrationHistory> findByName(String name);

    List<SourceRegistrationHistory> findAllByOrderByLastUsedAtDesc();

    Optional<SourceRegistrationHistory> findFirstByOrderByLastUsedAtDesc();

    boolean existsByName(String name);

    boolean existsByOpenApiUrl(String openApiUrl);

    boolean existsByBaseUrl(String baseUrl);

    Optional<SourceRegistrationHistory> findFirstByNameOrOpenApiUrlOrBaseUrl(
            String name,
            String openApiUrl,
            String baseUrl);
}
