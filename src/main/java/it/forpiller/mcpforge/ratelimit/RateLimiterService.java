package it.forpiller.mcpforge.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import it.forpiller.mcpforge.config.McpForgeProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting in-memory per coppia source+tool, con Bucket4j.
 * Un bucket per chiave, creato lazy alla prima chiamata.
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final McpForgeProperties properties;

    public RateLimiterService(McpForgeProperties properties) {
        this.properties = properties;
    }

    public boolean tryConsume(String sourceName, String toolName) {
        String key = sourceName + "::" + toolName;
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        McpForgeProperties.RateLimit config = properties.getRatelimit();
        Bandwidth limit = Bandwidth.classic(config.getCapacity(),
                Refill.greedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillPeriodSeconds())));
        return Bucket.builder().addLimit(limit).build();
    }
}
