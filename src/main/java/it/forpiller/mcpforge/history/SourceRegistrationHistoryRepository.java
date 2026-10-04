package it.forpiller.mcpforge.history;

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
}
