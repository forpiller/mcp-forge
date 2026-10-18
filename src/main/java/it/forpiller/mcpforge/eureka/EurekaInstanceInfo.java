package it.forpiller.mcpforge.eureka;

/**
 * Una singola istanza di un'applicazione registrata su Eureka, con l'URL
 * che mcp-forge propone come base URL — di default {@code localhost:<porta>},
 * perché nello scenario d'uso tipico (dev locale) mcp-forge e l'applicazione
 * scoperta girano sulla stessa macchina, anche quando Eureka stesso è
 * containerizzato e riporta hostname/IP interni Docker non raggiungibili
 * dall'host. hostName e ipAddr restano comunque visibili come alternative,
 * per i casi in cui l'app scoperta è anch'essa in un container raggiungibile.
 */
public record EurekaInstanceInfo(String hostName, String ipAddr, int port, String status, String suggestedBaseUrl) {
}
