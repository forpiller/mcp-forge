package it.forpiller.mcpforge.eureka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

public class EurekaDiscoveryService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String eurekaUrl;

    public EurekaDiscoveryService(RestClient restClient, String eurekaUrl) {
        this.restClient = restClient;
        this.eurekaUrl = eurekaUrl;
    }

    /** @throws RuntimeException se Eureka non è raggiungibile — il chiamante decide come mostrarlo. */
    public List<EurekaAppInfo> discover() {
        String body = restClient.get()
                .uri(eurekaUrl + "/apps")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        List<EurekaAppInfo> result = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode applications = root.path("applications").path("application");
            for (JsonNode appNode : asList(applications)) {
                String name = appNode.path("name").asText();
                List<EurekaInstanceInfo> instances = new ArrayList<>();
                for (JsonNode instanceNode : asList(appNode.path("instance"))) {
                    String status = instanceNode.path("status").asText("UNKNOWN");
                    if (!"UP".equals(status)) {
                        continue;
                    }
                    String hostName = instanceNode.path("hostName").asText(null);
                    String ipAddr = instanceNode.path("ipAddr").asText(null);
                    int port = instanceNode.path("port").path("$").asInt(0);
                    if (port == 0) {
                        continue;
                    }
                    instances.add(new EurekaInstanceInfo(hostName, ipAddr, port, status,
                            "http://localhost:" + port));
                }
                if (!instances.isEmpty()) {
                    result.add(new EurekaAppInfo(name, instances));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Risposta Eureka non valida: " + e.getMessage(), e);
        }

        return result;
    }

    /** Eureka serializza un elemento singolo come oggetto invece che come array di un elemento — quirk XML-to-JSON. */
    private List<JsonNode> asList(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (node.isArray()) {
            node.forEach(list::add);
        } else {
            list.add(node);
        }
        return list;
    }
}
