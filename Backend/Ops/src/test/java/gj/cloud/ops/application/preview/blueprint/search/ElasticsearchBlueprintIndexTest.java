package gj.cloud.ops.application.preview.blueprint.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchBlueprintIndexTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void rebuildsDerivedIndexAndParsesSearchHits() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        BlueprintSearchProperties properties = new BlueprintSearchProperties(
                true, url, "gamjabox-blueprints-test", "", "", false);
        ElasticsearchBlueprintIndex index = new ElasticsearchBlueprintIndex(properties, new ObjectMapper());
        var documents = BlueprintRegistryIndexProjection.projectAll().subList(0, 2);

        int count = index.rebuild(documents);
        var hits = index.search("admin workspace",
                documents.stream().map(document -> document.blueprintId()).toList(), 5);

        assertThat(count).isEqualTo(2);
        assertThat(hits).containsExactly(
                new ElasticsearchBlueprintIndex.SearchHit(documents.get(0).blueprintId(), 7.25));
        assertThat(requests).anyMatch(request -> request.startsWith("DELETE /gamjabox-blueprints-test"));
        assertThat(requests).anyMatch(request ->
                request.startsWith("PUT /gamjabox-blueprints-test") && request.contains("\"dynamic\":\"strict\""));
        assertThat(requests).anyMatch(request ->
                request.startsWith("POST /_bulk?refresh=wait_for") && request.contains("\"index\""));
        assertThat(requests).anyMatch(request ->
                request.startsWith("POST /gamjabox-blueprints-test/_search")
                        && request.contains(documents.get(0).blueprintId()));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + "\n" + body);
        String path = exchange.getRequestURI().getPath();
        int status = 200;
        String response;
        if ("DELETE".equals(exchange.getRequestMethod())) {
            status = 404;
            response = "{\"error\":{\"type\":\"index_not_found_exception\"}}";
        } else if (path.endsWith("/_search")) {
            String id = BlueprintRegistryIndexProjection.projectAll().get(0).blueprintId();
            response = """
                    {"hits":{"hits":[{"_id":"%s","_score":7.25,"_source":{"blueprintId":"%s"}}]}}
                    """.formatted(id, id);
        } else if (path.equals("/_bulk")) {
            response = "{\"errors\":false,\"items\":[]}";
        } else {
            response = "{\"acknowledged\":true}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
