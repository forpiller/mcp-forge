# mcp-forge

Genera automaticamente un server **MCP (Model Context Protocol)** a partire dallo spec
**OpenAPI** di un'API REST esistente. Ogni operazione dello spec diventa un tool MCP
invocabile da qualsiasi client MCP (Claude Desktop, MCP Inspector, ecc.), senza scrivere
a mano l'integrazione.

Ogni chiamata ai tool generati passa per:
- **Auth passthrough** — un header statico (API key / bearer token) configurato per fonte
- **Rate limiting** — limite di chiamate per finestra, per coppia fonte+tool (Bucket4j)
- **Audit log** — ogni invocazione viene registrata (fonte, tool, parametri, esito, durata)

## Stack

- Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 (MCP server, transport Streamable HTTP)
- `swagger-parser` per il parsing OpenAPI
- Bucket4j per il rate limiting
- H2 (file-based) per l'audit log — nessun Docker richiesto

## Come si usa

### Avvio

```bash
mvn spring-boot:run
```

L'app parte sulla porta `8090`.

### Registrare una fonte API

```bash
curl -X POST http://localhost:8090/api/sources \
  -H "Content-Type: application/json" \
  -d '{
        "name": "petstore",
        "openApiUrl": "https://petstore3.swagger.io/api/v3/openapi.json",
        "baseUrl": "https://petstore3.swagger.io/api/v3"
      }'
```

La risposta elenca i tool MCP generati (es. `petstore__findPetsByStatus`, `petstore__getOrderById`, ...).

### Connettersi come client MCP

Endpoint Streamable HTTP esposto da Spring AI: `http://localhost:8090/mcp`.
Per esplorarlo manualmente:

```bash
npx @modelcontextprotocol/inspector
```

e puntarlo su quell'URL — i tool generati compaiono subito, con lo schema di input
derivato dai parametri OpenAPI.

### Audit log

Console H2 su `http://localhost:8090/h2-console` (JDBC URL: `jdbc:h2:file:./data/mcp-forge`),
tabella `audit_log`.

## Fuori scope (MVP)

- Auth OAuth2 client-credentials (solo header statico per ora)
- Routing multi-tenant / dashboard enterprise
- Persistenza audit log su Postgres (qui H2 per semplicità della demo)
