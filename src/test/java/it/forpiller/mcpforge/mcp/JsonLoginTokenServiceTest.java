package it.forpiller.mcpforge.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonLoginTokenServiceTest {

    private JsonLoginTokenService service;

    @BeforeEach
    void setUp() {
        service = new JsonLoginTokenService(RestClient.create());
    }

    @Test
    void invalidateDoesNotThrowOnMissingEntry() {
        assertThatNoException().isThrownBy(() -> service.invalidate("fonte-inesistente"));
    }

    @Test
    void invalidateDoesNotThrowAfterDoubleCall() {
        service.invalidate("source-a");
        assertThatNoException().isThrownBy(() -> service.invalidate("source-a"));
    }

    @Test
    void getAccessTokenThrowsWhenServerUnreachable() {
        JsonLoginConfig config = new JsonLoginConfig(
                "http://localhost:19999/api/login", "user", "pass", "access_token");

        assertThatThrownBy(() -> service.getAccessToken("test", config))
                .isInstanceOf(Exception.class);
    }
}
