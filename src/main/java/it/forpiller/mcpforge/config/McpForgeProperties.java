package it.forpiller.mcpforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mcp-forge")
public class McpForgeProperties {

    private final RateLimit ratelimit = new RateLimit();
    private final Audit audit = new Audit();
    private final SpecWatcher specWatcher = new SpecWatcher();

    public RateLimit getRatelimit() {
        return ratelimit;
    }

    public Audit getAudit() {
        return audit;
    }

    public SpecWatcher getSpecWatcher() {
        return specWatcher;
    }

    public static class RateLimit {
        /** Numero massimo di chiamate per finestra, per coppia source+tool. */
        private long capacity = 10;
        /** Token ricaricati ad ogni finestra. */
        private long refillTokens = 10;
        /** Durata della finestra di ricarica, in secondi. */
        private long refillPeriodSeconds = 60;

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public long getRefillPeriodSeconds() {
            return refillPeriodSeconds;
        }

        public void setRefillPeriodSeconds(long refillPeriodSeconds) {
            this.refillPeriodSeconds = refillPeriodSeconds;
        }
    }

    public static class SpecWatcher {
        /** Abilita il controllo periodico delle modifiche allo spec OpenAPI. */
        private boolean enabled = true;
        /** Intervallo tra un controllo e il successivo, in millisecondi. Default: 60 secondi. */
        private long intervalMs = 60_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }

    public static class Audit {
        /** Nomi di campi (case-insensitive) da oscurare nel log di audit. */
        private List<String> redactFields = List.of("password", "token", "secret", "apiKey");

        public List<String> getRedactFields() {
            return redactFields;
        }

        public void setRedactFields(List<String> redactFields) {
            this.redactFields = redactFields;
        }
    }
}
