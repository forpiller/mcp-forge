package it.forpiller.mcpforge.repository;

import it.forpiller.mcpforge.audit.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findBySourceName(String sourceName, Pageable pageable);

    @Query("SELECT DISTINCT a.sourceName FROM AuditLogEntry a ORDER BY a.sourceName")
    List<String> findDistinctSourceNames();
}
