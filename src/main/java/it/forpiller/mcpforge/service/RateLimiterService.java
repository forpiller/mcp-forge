package it.forpiller.mcpforge.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import it.forpiller.mcpforge.config.McpForgeProperties;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

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
