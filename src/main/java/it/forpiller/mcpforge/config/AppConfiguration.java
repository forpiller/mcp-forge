package it.forpiller.mcpforge.config;

import io.modelcontextprotocol.server.McpSyncServer;
import tools.jackson.databind.ObjectMapper;
import it.forpiller.mcpforge.chat.ChatToolCallbackFactory;
import it.forpiller.mcpforge.eureka.EurekaDiscoveryService;
import it.forpiller.mcpforge.mcp.ApiInvoker;
import it.forpiller.mcpforge.mcp.CsrfTokenService;
import it.forpiller.mcpforge.mcp.JsonLoginTokenService;
import it.forpiller.mcpforge.mcp.McpToolFactory;
import it.forpiller.mcpforge.mcp.OAuth2TokenService;
import it.forpiller.mcpforge.mcp.SourceHealthCheckService;
import it.forpiller.mcpforge.mcp.SourceRegistry;
import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.repository.AuditLogRepository;
import it.forpiller.mcpforge.repository.SourceRegistrationHistoryRepository;
import it.forpiller.mcpforge.repository.SourceToolCacheRepository;
import it.forpiller.mcpforge.service.AuditLogService;
import it.forpiller.mcpforge.service.ChatHistoryService;
import it.forpiller.mcpforge.service.OpenApiIngestService;
import it.forpiller.mcpforge.service.PortsService;
import it.forpiller.mcpforge.service.RateLimiterService;
import it.forpiller.mcpforge.service.SourceRegistrationHistoryService;
import it.forpiller.mcpforge.service.SourceRegistrationService;
import it.forpiller.mcpforge.service.SourceToolCacheService;
import jakarta.servlet.SessionTrackingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;

@Configuration
@EnableScheduling
public class AppConfiguration {

    @Bean
    public ServletContextInitializer sessionCookieOnly() {
        return ctx -> ctx.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
    }

    @Bean
    public SourceRegistry sourceRegistry() {
        return new SourceRegistry();
    }

    @Bean
    public OAuth2TokenService oAuth2TokenService(RestClient restClient) {
        return new OAuth2TokenService(restClient);
    }

    @Bean
    public JsonLoginTokenService jsonLoginTokenService(RestClient restClient) {
        return new JsonLoginTokenService(restClient);
    }

    @Bean
    public CsrfTokenService csrfTokenService(RestClient restClient) {
        return new CsrfTokenService(restClient);
    }

    @Bean
    public ApiInvoker apiInvoker(
            RestClient restClient,
            OAuth2TokenService oAuth2TokenService,
            JsonLoginTokenService jsonLoginTokenService,
            CsrfTokenService csrfTokenService) {
        return new ApiInvoker(restClient, oAuth2TokenService, jsonLoginTokenService, csrfTokenService);
    }

    @Bean
    public RateLimiterService rateLimiterService(McpForgeProperties properties) {
        return new RateLimiterService(properties);
    }

    @Bean
    public AuditLogService auditLogService(
            AuditLogRepository auditLogRepository,
            McpForgeProperties properties) {
        return new AuditLogService(auditLogRepository, properties);
    }

    @Bean
    public McpToolFactory mcpToolFactory(
            ApiInvoker apiInvoker,
            RateLimiterService rateLimiterService,
            AuditLogService auditLogService) {
        return new McpToolFactory(apiInvoker, rateLimiterService, auditLogService);
    }

    @Bean
    public ToolRegistrar toolRegistrar(
            SourceRegistry sourceRegistry,
            McpToolFactory mcpToolFactory,
            McpSyncServer mcpSyncServer) {
        return new ToolRegistrar(sourceRegistry, mcpToolFactory, mcpSyncServer);
    }

    @Bean
    public OpenApiIngestService openApiIngestService(RestClient restClient) {
        return new OpenApiIngestService(restClient);
    }

    @Bean
    public SourceRegistrationHistoryService sourceRegistrationHistoryService(
            SourceRegistrationHistoryRepository repository) {
        return new SourceRegistrationHistoryService(repository);
    }

    @Bean
    public SourceToolCacheService sourceToolCacheService(
            SourceToolCacheRepository repository,
            ObjectMapper objectMapper) {
        return new SourceToolCacheService(repository, objectMapper);
    }

    @Bean
    public SourceRegistrationService sourceRegistrationService(
            OpenApiIngestService openApiIngestService,
            ToolRegistrar toolRegistrar,
            SourceRegistrationHistoryService historyService,
            OAuth2TokenService oAuth2TokenService,
            JsonLoginTokenService jsonLoginTokenService,
            SourceToolCacheService cacheService) {
        return new SourceRegistrationService(openApiIngestService, toolRegistrar, historyService,
                oAuth2TokenService, jsonLoginTokenService, cacheService);
    }

    @Bean
    public ChatHistoryService chatHistoryService() {
        return new ChatHistoryService();
    }

    @Bean
    public StartupSourceLoader startupSourceLoader(
            SourceRegistrationHistoryService historyService,
            SourceRegistrationService sourceRegistrationService,
            SourceToolCacheService cacheService) {
        return new StartupSourceLoader(historyService, sourceRegistrationService, cacheService);
    }

    @Bean
    public EurekaDiscoveryService eurekaDiscoveryService(
            RestClient restClient,
            @Value("${mcp-forge.eureka.url:http://localhost:8761/eureka}") String eurekaUrl) {
        return new EurekaDiscoveryService(restClient, eurekaUrl);
    }

    @Bean
    public ChatToolCallbackFactory chatToolCallbackFactory(
            SourceRegistry sourceRegistry,
            McpToolFactory mcpToolFactory) {
        return new ChatToolCallbackFactory(sourceRegistry, mcpToolFactory);
    }

    @Bean
    public SourceHealthCheckService sourceHealthCheckService(
            SourceRegistry sourceRegistry,
            ApiInvoker apiInvoker) {
        return new SourceHealthCheckService(sourceRegistry, apiInvoker);
    }

    @Bean
    public PortsService portsService(
            SourceRegistrationHistoryRepository repository,
            @Value("${server.port:8090}") int serverPort) {
        return new PortsService(repository, serverPort);
    }

    @Bean
    public SpecChangeWatcherService specChangeWatcherService(
            SourceRegistry sourceRegistry,
            SourceRegistrationHistoryService historyService,
            SourceRegistrationService sourceRegistrationService,
            McpForgeProperties properties) {
        return new SpecChangeWatcherService(sourceRegistry, historyService, sourceRegistrationService, properties);
    }
}
