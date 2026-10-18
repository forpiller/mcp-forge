package it.forpiller.mcpforge.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class SourceHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(SourceHealthCheckService.class);

    private final SourceRegistry sourceRegistry;
    private final ApiInvoker apiInvoker;

    public SourceHealthCheckService(SourceRegistry sourceRegistry, ApiInvoker apiInvoker) {
        this.sourceRegistry = sourceRegistry;
        this.apiInvoker = apiInvoker;
    }

    /**
     * Trova la prima GET senza path variables e la chiama per verificare che il
     * servizio risponda correttamente.
     */
    public Optional<HealthCheckResult> check(String sourceName) {
        Optional<RegisteredTool> candidate = sourceRegistry.all().stream()
                .filter(t -> t.source().name().equals(sourceName))
                .filter(t -> "GET".equalsIgnoreCase(t.operation().httpMethod()))
                .filter(t -> !t.operation().path().contains("{"))
                .findFirst();

        if (candidate.isEmpty()) {
            log.debug("HealthCheck: nessuna GET senza path variables per '{}'", sourceName);
            return Optional.empty();
        }

        RegisteredTool tool = candidate.get();
        try {
            ApiCallResult result = apiInvoker.invoke(tool, Map.of());
            boolean ok = result.success() && result.httpStatus() >= 200 && result.httpStatus() < 300;
            return Optional.of(new HealthCheckResult(tool.operation().path(), result.httpStatus(), ok, result.errorMessage()));
        } catch (Exception e) {
            log.warn("HealthCheck fallito per '{}': {}", sourceName, e.getMessage());
            return Optional.of(new HealthCheckResult(tool.operation().path(), 0, false, e.getMessage()));
        }
    }
}
