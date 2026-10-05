package gj.cloud.ops.application.deployment.service;
import gj.cloud.ops.application.deployment.dto.*;
import gj.cloud.ops.application.deployment.routing.ComposeRouterPlanner;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class ComposePreparationServiceTest {
 final ComposePreparationService service = new ComposePreparationService(new ComposeValidator());
 final String compose = "services:\n  web:\n    image: nginx:alpine\n    ports: ['18080:80']\n  api:\n    image: example/api\n    expose: [8080]\n";
 @Test void selectedEnvironmentServicesAndContextArePreserved() {
  var env = List.of(new EnvironmentFile("config/app.env", "TOKEN=fixture", List.of("api")));
  var result = service.inspect(new ComposePreparationRequest(compose, env, List.of(), List.of(), "subproject"));
  assertThat(result.valid()).isTrue();
  assertThat(result.environmentFiles()).containsExactly(new EnvironmentFile("subproject/config/app.env", "TOKEN=fixture"));
  assertThat(result.composeContent()).contains("env_file:").contains("config/app.env");
  assertThat(result.healthChecks()).extracting(HealthCheck::serviceName).containsExactly("web", "api");
  var again = service.inspect(new ComposePreparationRequest(result.composeContent(), env, List.of(), result.healthChecks(), "subproject"));
  assertThat(again.composeContent()).isEqualTo(result.composeContent());
  assertThat(again.healthChecks()).hasSize(2);
 }
 @Test void preservesFinalYamlAndLegacyUploads() {
  var result = service.inspect(new ComposePreparationRequest(compose, List.of(new EnvironmentFile("nested/.env", "PASSWORD=fixed")), List.of(), List.of(), null));
  assertThat(result.valid()).isTrue(); assertThat(result.composeContent()).isEqualTo(compose);
  assertThat(result.environmentFiles()).containsExactly(new EnvironmentFile("nested/.env", "PASSWORD=fixed"));
 }
 @Test void routesMustMatchHostPort() {
  for (int port : List.of(80, 18080)) {
   var result = service.inspect(new ComposePreparationRequest(compose, List.of(), List.of(new ExposedRoute("web", port, "HTTP", "PUBLIC", "web", null)), List.of(), null));
   assertThat(result.valid()).isEqualTo(port == 18080);
  }
 }
 @Test void missingTargetsAndDuplicatePathsFailWithoutExposingValues() {
  var result = service.inspect(new ComposePreparationRequest(compose, List.of(new EnvironmentFile(".env", "SECRET=do-not-display", List.of("missing")), new EnvironmentFile(".env", "X=1", List.of())), List.of(), List.of(), null));
  assertThat(result.valid()).isFalse(); assertThat(result.errors()).hasSize(2);
  assertThat(result.errors().toString()).doesNotContain("do-not-display");
 }
 @Test void caddyEditsReachFinalComposeAndGatewayRoutesAreRecognized() {
  var plan = new ComposeRouterPlanner().plan(compose, 18080, Map.of());
  var result = service.inspect(new ComposePreparationRequest(plan.enhancedComposeContent(), List.of(), List.of(new ExposedRoute("api", 18080, "HTTP", "PUBLIC", "api", "api")), List.of(), null, plan.routerConfig().replace("reverse_proxy api:8080", "reverse_proxy api:8080 # edited")));
  assertThat(result.valid()).isTrue(); assertThat(result.composeContent()).contains("# edited");
 }
 @Test void loopbackAndUdpBindingsCannotBeTunnelTargets() {
  for (String binding : List.of("127.0.0.1:18080:80", "18080:80/udp")) {
   var result = service.inspect(new ComposePreparationRequest(compose.replace("18080:80", binding), List.of(), List.of(new ExposedRoute("web", 18080, "HTTP", "PUBLIC", "web", null)), List.of(), null));
   assertThat(result.valid()).isFalse();
  }
 }
 @Test void interpolationUsesComposeDotEnvAndKeepsOriginalExpressions() {
  String yaml = compose.replace("18080:80", "${HTTP_PORT:-18080}:80");
  var result = service.inspect(new ComposePreparationRequest(yaml, List.of(new EnvironmentFile(".env", "HTTP_PORT=19080", List.of())), List.of(new ExposedRoute("web",19080,"HTTP","PUBLIC","web",null)), List.of(), null));
  assertThat(result.valid()).isTrue(); assertThat(result.composeContent()).isEqualTo(yaml);
  assertThat(result.services().get(0).hostPorts()).containsExactly(19080);
 }
 @Test void deselectingEnvironmentRemovesPreviouslyManagedReferences() {
  var original = service.inspect(new ComposePreparationRequest(compose,List.of(new EnvironmentFile(".env","X=1",List.of("api"))),List.of(),List.of(),null));
  var edited = service.inspect(new ComposePreparationRequest(original.composeContent(),List.of(new EnvironmentFile(".env","X=1",List.of())),List.of(),List.of(),null));
  assertThat(edited.valid()).isTrue(); assertThat(edited.composeContent()).doesNotContain("env_file");
 }
 @Test void restoresLegacyServiceSelectionsWithoutDuplicatingContext() {
  String yaml = compose.replace("expose: [8080]", "expose: [8080]\n    env_file: [config/api.env]");
  var artifact = new ComposeArtifact(yaml,List.of(new EnvironmentFile("module/config/api.env","X=1")),List.of(),List.of(),List.of(),gj.cloud.ops.domain.deployment.enums.SourceType.RAW_COMPOSE);
  var editable = service.editableEnvironmentFiles(artifact,"module");
  assertThat(editable).containsExactly(new EnvironmentFile("config/api.env","X=1",List.of("api")));
 }
}
