package it.forpiller.mcpforge.eureka;

import java.util.List;

/** Un'applicazione registrata su Eureka con le sue istanze attive. */
public record EurekaAppInfo(String name, List<EurekaInstanceInfo> instances) {
}
