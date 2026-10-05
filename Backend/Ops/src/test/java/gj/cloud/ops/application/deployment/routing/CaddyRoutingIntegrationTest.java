package gj.cloud.ops.application.deployment.routing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CaddyRoutingIntegrationTest {
 @TempDir Path directory;
 @Test void actualCaddyValidatesAndRoutesRootPrefixDomainAndHealth() throws Exception {
  String binary = System.getenv("CADDY_TEST_BINARY");
  assumeTrue(binary != null && Files.isExecutable(Path.of(binary)), "CADDY_TEST_BINARY points to a real Caddy executable");
  HttpServer app = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  app.createContext("/", exchange -> { byte[] body = ("upstream:" + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close(); });
  app.start();
  int port; try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
  var planner = new ComposeRouterPlanner();
  String compose = "services:\n  web:\n    image: nginx\n    expose: [80]\n  api:\n    image: example/api\n    expose: [8080]\n  community:\n    image: example/community\n    expose: [8082]\n";
  var plan = planner.plan(compose, port, Map.of(), Map.of("api", new ComposeRouterRouteOverride("PREFIX", "/api", true, null), "community", new ComposeRouterRouteOverride("DOMAIN", null, false, "community")), Set.of());
  String docker = System.getenv("DOCKER_TEST_BINARY");
  if (docker != null) {
   Path composeFile = directory.resolve("compose.yaml"); Files.writeString(composeFile, plan.enhancedComposeContent());
   Process config = new ProcessBuilder(docker, "compose", "-f", composeFile.toString(), "config", "--format", "json").redirectErrorStream(true).start();
   String normalized = new String(config.getInputStream().readAllBytes());
   assertThat(config.waitFor()).as(normalized).isZero();
   var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(normalized);
   assertThat(parsed.path("services").path("gamjabox-router").path("command").size()).isEqualTo(1);
   assertThat(parsed.path("services").path("gamjabox-router").path("command").get(0).asText()).contains("exec caddy run");
  }
  String code = "{\n admin off\n}\n" + plan.routerConfig().replace(":8080 {", ":" + port + " {")
       .replace("web:80", "127.0.0.1:" + app.getAddress().getPort()).replace("api:8080", "127.0.0.1:" + app.getAddress().getPort()).replace("community:8082", "127.0.0.1:" + app.getAddress().getPort());
  Path file = directory.resolve("Caddyfile"); Files.writeString(file, code);
  Process validation = new ProcessBuilder(binary, "validate", "--config", file.toString(), "--adapter", "caddyfile").redirectErrorStream(true).start();
  String output = new String(validation.getInputStream().readAllBytes()); assertThat(validation.waitFor()).as(output).isZero();
  Process caddy = new ProcessBuilder(binary, "run", "--config", file.toString(), "--adapter", "caddyfile").redirectErrorStream(true).redirectOutput(directory.resolve("caddy.log").toFile()).start();
  try {
   boolean ready = false;
   for (int attempt=0; attempt<50; attempt++) { try { request(port, "example.test", "/"); ready=true; break; } catch (Exception ignored) { Thread.sleep(100); } }
   assertThat(ready).isTrue();
   assertThat(request(port, "example.test", "/hello")).contains("upstream:/hello");
   assertThat(request(port, "example.test", "/api/items")).contains("upstream:/items");
   assertThat(request(port, "community.example.test", "/topic")).contains("upstream:/topic");
   assertThat(request(port, "example.test", "/__gamjabox_router_health")).contains("200 OK");
   var restored = planner.plan(plan.enhancedComposeContent(), null, Map.of());
   assertThat(restored.routes()).extracting(ComposeRouterRoute::serviceName).containsExactlyInAnyOrder("web", "api", "community");
   var switched = planner.plan(plan.enhancedComposeContent(), port, Map.of(), Map.of("web", new ComposeRouterRouteOverride("PREFIX", "/web", false, null), "api", new ComposeRouterRouteOverride("PREFIX", "/", false, null)), Set.of("community"), true);
   assertThat(switched.routes().get(0).serviceName()).isEqualTo("api");
  } finally { caddy.destroy(); caddy.waitFor(); app.stop(0); }
 }
 private String request(int port, String host, String path) throws Exception {
  try (Socket socket = new Socket("127.0.0.1", port)) {
   socket.setSoTimeout(2000); socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
   return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }
 }
}
