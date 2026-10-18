package it.forpiller.mcpforge.web;

import it.forpiller.mcpforge.eureka.EurekaAppInfo;
import it.forpiller.mcpforge.eureka.EurekaDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Endpoint AJAX per la tab "Eureka" della home: elenca le applicazioni UP sul registro locale. */
@RestController
public class EurekaController {

    private final EurekaDiscoveryService eurekaDiscoveryService;

    public EurekaController(EurekaDiscoveryService eurekaDiscoveryService) {
        this.eurekaDiscoveryService = eurekaDiscoveryService;
    }

    @GetMapping("/eureka/apps")
    public ResponseEntity<?> apps() {
        try {
            List<EurekaAppInfo> apps = eurekaDiscoveryService.discover();
            return ResponseEntity.ok(apps);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }
}
