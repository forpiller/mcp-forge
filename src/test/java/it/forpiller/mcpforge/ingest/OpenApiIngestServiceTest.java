package it.forpiller.mcpforge.ingest;

import it.forpiller.mcpforge.service.OpenApiIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiIngestServiceTest {

    private OpenApiIngestService service;

    @BeforeEach
    void setUp() {
        service = new OpenApiIngestService(RestClient.create());
    }

    @Test
    void parsesMultipleOperations() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Test", "version": "1.0"},
                  "paths": {
                    "/items": {
                      "get": {"operationId": "listItems", "summary": "List items",
                              "responses": {"200": {"description": "OK"}}},
                      "post": {"operationId": "createItem", "summary": "Create item",
                               "responses": {"201": {"description": "Created"}}}
                    },
                    "/items/{id}": {
                      "get": {
                        "operationId": "getItem",
                        "parameters": [{"name": "id", "in": "path", "required": true, "schema": {"type": "integer"}}],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);

        assertThat(ops).hasSize(3);
        assertThat(ops).anyMatch(op -> op.toolName().equals("listItems") && op.httpMethod().equals("GET"));
        assertThat(ops).anyMatch(op -> op.toolName().equals("createItem") && op.httpMethod().equals("POST"));
        assertThat(ops).anyMatch(op -> op.toolName().equals("getItem") && op.path().equals("/items/{id}"));
    }

    @Test
    void extractsPathParameters() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Test", "version": "1.0"},
                  "paths": {
                    "/users/{userId}/orders/{orderId}": {
                      "get": {
                        "operationId": "getOrder",
                        "parameters": [
                          {"name": "userId", "in": "path", "required": true, "schema": {"type": "integer"}},
                          {"name": "orderId", "in": "path", "required": true, "schema": {"type": "string"}},
                          {"name": "expand", "in": "query", "required": false, "schema": {"type": "boolean"}}
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);

        assertThat(ops).hasSize(1);
        OperationDescriptor op = ops.get(0);
        assertThat(op.parameters()).hasSize(3);
        assertThat(op.parameters()).anyMatch(p -> p.name().equals("userId") && p.location() == ParamLocation.PATH);
        assertThat(op.parameters()).anyMatch(p -> p.name().equals("orderId") && p.location() == ParamLocation.PATH);
        assertThat(op.parameters()).anyMatch(p -> p.name().equals("expand") && p.location() == ParamLocation.QUERY);
    }

    @Test
    void sanitizesSpecialCharsInToolName() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Test", "version": "1.0"},
                  "paths": {
                    "/items": {
                      "get": {
                        "operationId": "list-items/v2 (new)",
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).toolName()).matches("[a-zA-Z0-9_-]+");
    }

    @Test
    void throwsOnInvalidSpec() {
        assertThatThrownBy(() -> service.parseContent("not a valid openapi spec {}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsEmptyListForSpecWithNoPaths() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Empty", "version": "1.0"},
                  "paths": {}
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);
        assertThat(ops).isEmpty();
    }

    @Test
    void inheritsPathLevelParameters() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Test", "version": "1.0"},
                  "paths": {
                    "/items/{itemId}": {
                      "parameters": [
                        {"name": "itemId", "in": "path", "required": true, "schema": {"type": "string"}}
                      ],
                      "get": {
                        "operationId": "getItem",
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).parameters())
                .anyMatch(p -> p.name().equals("itemId") && p.location() == ParamLocation.PATH);
    }

    @Test
    void addsUndeclaredPathVariableAsPathParam() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Test", "version": "1.0"},
                  "paths": {
                    "/widgets/{widgetId}": {
                      "get": {
                        "operationId": "getWidget",
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        List<OperationDescriptor> ops = service.parseContent(spec);

        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).parameters())
                .anyMatch(p -> p.name().equals("widgetId") && p.location() == ParamLocation.PATH);
    }
}
