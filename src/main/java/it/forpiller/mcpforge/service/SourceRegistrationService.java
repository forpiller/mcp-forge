package it.forpiller.mcpforge.service;

import it.forpiller.mcpforge.ingest.OperationDescriptor;
import it.forpiller.mcpforge.mcp.JsonLoginConfig;
import it.forpiller.mcpforge.mcp.JsonLoginTokenService;
import it.forpiller.mcpforge.mcp.OAuth2PasswordGrantConfig;
import it.forpiller.mcpforge.mcp.OAuth2TokenService;
import it.forpiller.mcpforge.mcp.SourceConfig;
import it.forpiller.mcpforge.mcp.ToolRegistrar;
import it.forpiller.mcpforge.web.dto.SourceRegistrationInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class SourceRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(SourceRegistrationService.class);

    private final OpenApiIngestService openApiIngestService;
    private final ToolRegistrar toolRegistrar;
    private final SourceRegistrationHistoryService historyService;
    private final OAuth2TokenService oAuth2TokenService;
    private final JsonLoginTokenService jsonLoginTokenService;
    private final SourceToolCacheService cacheService;

    public SourceRegistrationService(
            OpenApiIngestService openApiIngestService,
            ToolRegistrar toolRegistrar,
            SourceRegistrationHistoryService historyService,
            OAuth2TokenService oAuth2TokenService,
            JsonLoginTokenService jsonLoginTokenService,
            SourceToolCacheService cacheService) {
        this.openApiIngestService = openApiIngestService;
        this.toolRegistrar = toolRegistrar;
        this.historyService = historyService;
        this.oAuth2TokenService = oAuth2TokenService;
        this.jsonLoginTokenService = jsonLoginTokenService;
        this.cacheService = cacheService;
    }

    public List<String> register(SourceRegistrationInput input) {
        OAuth2PasswordGrantConfig oauth2 = buildOAuth2Config(input);
        JsonLoginConfig jsonLogin = buildJsonLoginConfig(input);

        String rawSpec = fetchRawSpec(input, oauth2, jsonLogin);
        String specHash = sha256Hex(rawSpec);
        List<OperationDescriptor> operations = openApiIngestService.parseContent(rawSpec);

        SourceConfig source = new SourceConfig(input.name(), input.baseUrl(),
                input.authHeaderName(), input.authHeaderValue(), oauth2, jsonLogin);

        List<String> toolNames = toolRegistrar.registerSource(source, operations);
        historyService.saveOrUpdate(input);
        cacheService.save(input.name(), operations, specHash);
        log.info("Fonte '{}' registrata: {} tool generati", input.name(), toolNames.size());
        return toolNames;
    }

    public boolean refreshIfChanged(SourceRegistrationInput input) {
        OAuth2PasswordGrantConfig oauth2 = buildOAuth2Config(input);
        JsonLoginConfig jsonLogin = buildJsonLoginConfig(input);

        String rawSpec = fetchRawSpec(input, oauth2, jsonLogin);
        String newHash = sha256Hex(rawSpec);
        String storedHash = cacheService.findSpecHash(input.name()).orElse(null);

        if (storedHash == null) {
            List<OperationDescriptor> ops = openApiIngestService.parseContent(rawSpec);
            cacheService.save(input.name(), ops, newHash);
            log.debug("Hash spec inizializzato per '{}'", input.name());
            return false;
        }

        if (storedHash.equals(newHash)) {
            return false;
        }

        log.info("Spec cambiata per '{}' — aggiornamento tool MCP", input.name());
        toolRegistrar.unregisterSource(input.name());
        List<OperationDescriptor> ops = openApiIngestService.parseContent(rawSpec);
        SourceConfig source = new SourceConfig(input.name(), input.baseUrl(),
                input.authHeaderName(), input.authHeaderValue(), oauth2, jsonLogin);
        toolRegistrar.registerSource(source, ops);
        cacheService.save(input.name(), ops, newHash);
        return true;
    }

    public List<String> registerWithCachedOps(
            SourceRegistrationInput input,
            List<OperationDescriptor> operations) {
        OAuth2PasswordGrantConfig oauth2 = input.hasOAuth2()
                ? new OAuth2PasswordGrantConfig(input.oauth2TokenUrl(), input.oauth2ClientId(),
                        input.oauth2ClientSecret(), input.oauth2Username(), input.oauth2Password())
                : null;

        JsonLoginConfig jsonLogin = input.hasJsonLogin()
                ? new JsonLoginConfig(input.jsonLoginUrl(), input.jsonLoginUsername(),
                        input.jsonLoginPassword(), input.jsonLoginTokenPath())
                : null;

        SourceConfig source = new SourceConfig(input.name(), input.baseUrl(),
                input.authHeaderName(), input.authHeaderValue(), oauth2, jsonLogin);

        List<String> toolNames = toolRegistrar.registerSource(source, operations);
        log.info("Fonte '{}' caricata da cache: {} tool", input.name(), toolNames.size());
        return toolNames;
    }

    private OAuth2PasswordGrantConfig buildOAuth2Config(SourceRegistrationInput input) {
        return input.hasOAuth2()
                ? new OAuth2PasswordGrantConfig(input.oauth2TokenUrl(), input.oauth2ClientId(),
                        input.oauth2ClientSecret(), input.oauth2Username(), input.oauth2Password())
                : null;
    }

    private JsonLoginConfig buildJsonLoginConfig(SourceRegistrationInput input) {
        return input.hasJsonLogin()
                ? new JsonLoginConfig(input.jsonLoginUrl(), input.jsonLoginUsername(),
                        input.jsonLoginPassword(), input.jsonLoginTokenPath())
                : null;
    }

    private String fetchRawSpec(
            SourceRegistrationInput input,
            OAuth2PasswordGrantConfig oauth2,
            JsonLoginConfig jsonLogin) {
        try {
            return openApiIngestService.fetchSpecContent(input.openApiUrl(),
                    input.authHeaderName(), input.authHeaderValue());
        } catch (Exception specEx) {
            log.debug("Fetch spec senza auth fallito, riprovo con token: {}", specEx.getMessage());
            String headerName = input.authHeaderName();
            String headerValue = input.authHeaderValue();
            if (oauth2 != null) {
                headerName = "Authorization";
                headerValue = "Bearer " + oAuth2TokenService.getAccessToken(input.name(), oauth2);
            } else if (jsonLogin != null) {
                headerName = "Authorization";
                headerValue = "Bearer " + jsonLoginTokenService.getAccessToken(input.name(), jsonLogin);
            }
            return openApiIngestService.fetchSpecContent(input.openApiUrl(), headerName, headerValue);
        }
    }

    private static String sha256Hex(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
