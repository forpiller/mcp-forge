package it.forpiller.mcpforge.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketProperties;
import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

/**
 * On macOS, Tomcat calls setSoLinger(false, soLingerTime) which fails at the OS level when
 * soLingerTime is negative (-1). This customizer nulls out both soLingerOn and soLingerTime on
 * the endpoint's SocketProperties so the call is skipped entirely.
 */
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatWebServerFactory> soLingerFix() {
        return factory -> factory.addConnectorCustomizers(TomcatConfig::clearSoLinger);
    }

    private static void clearSoLinger(Connector connector) {
        try {
            if (!(connector.getProtocolHandler() instanceof AbstractProtocol<?> protocol)) {
                return;
            }
            Field endpointField = AbstractProtocol.class.getDeclaredField("endpoint");
            endpointField.setAccessible(true);
            AbstractEndpoint<?, ?> endpoint = (AbstractEndpoint<?, ?>) endpointField.get(protocol);

            SocketProperties props = endpoint.getSocketProperties();
            Field soLingerOn = SocketProperties.class.getDeclaredField("soLingerOn");
            soLingerOn.setAccessible(true);
            soLingerOn.set(props, null);

            Field soLingerTime = SocketProperties.class.getDeclaredField("soLingerTime");
            soLingerTime.setAccessible(true);
            soLingerTime.set(props, null);
        } catch (Exception ignored) {
        }
    }
}
