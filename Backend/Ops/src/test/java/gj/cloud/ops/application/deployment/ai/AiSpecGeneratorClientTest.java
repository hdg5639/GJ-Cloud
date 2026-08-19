package gj.cloud.ops.application.deployment.ai;

import com.openai.client.OpenAIClient;
import gj.cloud.ops.application.deployment.dto.ServiceCard;
import gj.cloud.ops.application.deployment.repoanalysis.DiscoveredService;
import gj.cloud.ops.application.deployment.repoanalysis.RepositorySnapshotBuilder;
import gj.cloud.ops.application.deployment.repoanalysis.RuleBasedSpecInferrer;
import gj.cloud.ops.application.deployment.spec.ArtifactSpec;
import gj.cloud.ops.application.deployment.spec.ArtifactType;
import gj.cloud.ops.application.deployment.spec.BuildRunStrategy;
import gj.cloud.ops.application.deployment.spec.BuildSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentMode;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecPolicyValidator;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecValidator;
import gj.cloud.ops.application.deployment.spec.RunSpec;
import gj.cloud.ops.application.deployment.spec.RuntimeKind;
import gj.cloud.ops.application.deployment.spec.ServiceSpec;
import gj.cloud.ops.domain.deployment.repository.AiSpecGenerationLogRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiSpecGeneratorClientTest {

    private final AiSpecGeneratorClient client = new AiSpecGeneratorClient(
            mock(OpenAIClient.class),
            "standard",
            "escalated",
            mock(DeploymentSpecValidator.class),
            mock(DeploymentSpecPolicyValidator.class),
            mock(AiSpecGenerationLogRepository.class),
            mock(RepositorySnapshotBuilder.class),
            mock(RuleBasedSpecInferrer.class),
            mock(AmbiguityScorer.class),
            mock(AiGenerationCache.class));

    @Test
    void restoresUserRequestedExposureWhenAiOmitsExposeSpec() {
        ServiceSpec generated = service(null);
        ServiceCard requested = card(true, "commerce");

        ServiceSpec merged = client.applyRequestedExposure(
                List.of(generated), List.of(requested)).get(0);

        assertThat(merged.expose()).isNotNull();
        assertThat(merged.expose().enabled()).isTrue();
        assertThat(merged.expose().protocol()).isEqualTo("http");
        assertThat(merged.expose().customSubdomain()).isEqualTo("commerce");
    }

    @Test
    void removesExposureWhenUserDidNotRequestIt() {
        ServiceSpec generated = service(
                new gj.cloud.ops.application.deployment.spec.ExposeSpec(
                        true, "http", "/", null, null, null, null));

        ServiceSpec merged = client.applyRequestedExposure(
                List.of(generated), List.of(card(false, null))).get(0);

        assertThat(merged.expose()).isNull();
    }

    @Test
    void discoveredModulesReplaceTheDefaultRootHint() {
        ServiceCard defaultHint = new ServiceCard(
                "app", "java", ".", 8080,
                21, "maven", null, null, null, null, null, true, null);
        List<DiscoveredService> discovered = List.of(
                discovered("commerce-api", "commerce-api", 18081),
                discovered("community-api", "community-api", 18082));

        List<ServiceCard> merged = client.mergeDiscoveredServices(List.of(defaultHint), discovered);

        assertThat(merged).extracting(ServiceCard::name)
                .containsExactly("commerce-api", "community-api");
        assertThat(merged).extracting(ServiceCard::context)
                .containsExactly("commerce-api", "community-api");
    }

    @Test
    void matchingHintOnlyOverridesTheDiscoveredServiceItDescribes() {
        ServiceCard hint = new ServiceCard(
                "commerce-api", "java", "commerce-api", 9090,
                17, "gradle", null, null, null, null, null, false, null);
        List<DiscoveredService> discovered = List.of(
                discovered("commerce-api", "commerce-api", 18081),
                discovered("community-api", "community-api", 18082));

        List<ServiceCard> merged = client.mergeDiscoveredServices(List.of(hint), discovered);

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).containerPort()).isEqualTo(9090);
        assertThat(merged.get(0).buildTool()).isEqualTo("gradle");
        assertThat(merged.get(1).containerPort()).isEqualTo(18082);
    }

    @Test
    void dockerfileExposeOverridesStaleHintPort() {
        ServiceCard hint = new ServiceCard(
                "web", "node", ".", 3000,
                null, null, 22, null, null, null, null, true, null);
        DiscoveredService discovered = new DiscoveredService(
                "web", ".", "node", 80,
                DiscoveredService.PORT_SOURCE_DOCKERFILE_EXPOSE,
                true, "HIGH", List.of("package.json", "Dockerfile EXPOSE 80"));

        List<ServiceCard> merged = client.mergeDiscoveredServices(List.of(hint), List.of(discovered));

        assertThat(merged).singleElement().satisfies(card -> assertThat(card.containerPort()).isEqualTo(80));
    }

    private ServiceSpec service(gj.cloud.ops.application.deployment.spec.ExposeSpec expose) {
        return new ServiceSpec(
                "commerce-api",
                DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.JAVA, "21", BuildRunStrategy.MAVEN_PACKAGE, null),
                new ArtifactSpec(ArtifactType.JAR, "target"),
                new RunSpec(RuntimeKind.JAVA, BuildRunStrategy.JAVA_JAR, 8080),
                "commerce-api",
                expose);
    }

    private ServiceCard card(boolean expose, String customSubdomain) {
        return new ServiceCard(
                "commerce-api", "java", "commerce-api", 8080,
                21, "maven", null, null, null, null, null, expose, customSubdomain);
    }

    private DiscoveredService discovered(String name, String context, int port) {
        return new DiscoveredService(
                name, context, "java", port, DiscoveredService.PORT_SOURCE_APPLICATION_CONFIG, true, "HIGH",
                List.of("pom.xml", "Spring Boot 실행 모듈"));
    }
}
