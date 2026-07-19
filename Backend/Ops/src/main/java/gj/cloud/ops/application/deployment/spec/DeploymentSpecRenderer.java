package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.EnvironmentFile;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// DeploymentSpec(JSON) → ComposeArtifact 렌더링. D-1(폼 입력)과 D-3(AI 생성)이 이 렌더러를 공유함 (F절 7·9단계).
// 렌더링 결과(composeContent)도 D-2와 동일하게 공통 Validator를 그대로 통과해야 함 — 이 클래스에서는 검증하지 않고
// DeploymentExecutor.enqueue()에 위임되는 기존 경로를 그대로 재사용함으로써 자동으로 보장됨.
@Component
@RequiredArgsConstructor
public class DeploymentSpecRenderer {

    private static final String DOCKERFILE_NAME = "Dockerfile.gamjabox";
    // DB는 expose.enabled=false가 기본(내부 전용, 호스트/외부에 노출 안 됨)이므로 간단한 기본 자격증명을 사용해도
    // 실질적 노출 위험이 낮음 — 네트워크 격리가 실제 보안 경계임 (전형적인 내부 전용 compose 관례)
    private static final String DEFAULT_DB_PASSWORD = "gamjabox";

    private final DockerfileGenerator dockerfileGenerator;

    public ComposeArtifact render(DeploymentSpec spec) {
        Map<String, Object> services = new LinkedHashMap<>();
        List<UploadedFile> uploadedFiles = new ArrayList<>();
        List<ExposedRoute> exposedRoutes = new ArrayList<>();
        List<HealthCheck> healthChecks = new ArrayList<>();

        for (ServiceSpec service : spec.services()) {
            boolean isStatic = service.artifact().type() == ArtifactType.STATIC_DIRECTORY;
            int effectivePort = isStatic ? 80 : (service.run().containerPort() != null ? service.run().containerPort() : 0);

            Map<String, Object> serviceBlock = new LinkedHashMap<>();
            Map<String, Object> build = new LinkedHashMap<>();
            build.put("context", service.context());

            boolean useExistingDockerfile = service.build().strategy() == BuildRunStrategy.DOCKERFILE;
            if (useExistingDockerfile) {
                // 저장소에 이미 있는 Dockerfile을 그대로 사용 — 우리가 생성하지 않음 (D-1/D-3과 달리 D-2 raw
                // compose 흐름과 동일하게 사용자가 제공한 빌드 정의가 그대로 authoritative함)
                build.put("dockerfile", "Dockerfile");
            } else {
                build.put("dockerfile", DOCKERFILE_NAME);
            }
            serviceBlock.put("build", build);
            serviceBlock.put("container_name", service.name());
            serviceBlock.put("restart", "unless-stopped");
            serviceBlock.put("networks", List.of(spec.network()));

            boolean exposed = service.expose() != null && service.expose().enabled();
            if (exposed) {
                // Cloudflare Tunnel이 VM 호스트에서 붙으므로 외부 노출 대상은 호스트 포트 바인딩이 필요함
                serviceBlock.put("ports", List.of(effectivePort + ":" + effectivePort));
            }
            services.put(service.name(), serviceBlock);

            if (!useExistingDockerfile) {
                String dockerfileContent = dockerfileGenerator.generate(service);
                uploadedFiles.add(new UploadedFile(service.context() + "/" + DOCKERFILE_NAME,
                        dockerfileContent.getBytes(StandardCharsets.UTF_8)));
            }

            if (exposed) {
                String protocol = "http".equalsIgnoreCase(service.expose().protocol()) ? "HTTP" : "TCP";
                exposedRoutes.add(new ExposedRoute(service.name(), effectivePort, protocol, "PUBLIC", service.name()));

                String healthPath = service.expose().healthCheckPath();
                if (healthPath != null && !healthPath.isBlank()) {
                    healthChecks.add(new HealthCheck(service.name(), healthPath, effectivePort, null));
                }
            }
        }

        List<EnvironmentFile> environmentFiles = new ArrayList<>();
        if (spec.infrastructure() != null) {
            for (InfrastructureSpec infra : spec.infrastructure()) {
                renderInfrastructure(infra, spec.network(), services);
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("services", services);
        Map<String, Object> networkDef = new LinkedHashMap<>();
        networkDef.put(spec.network(), Map.of());
        root.put("networks", networkDef);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String composeContent = new Yaml(options).dump(root);

        return new ComposeArtifact(composeContent, environmentFiles, uploadedFiles, exposedRoutes, healthChecks, SourceType.TEMPLATE_SPEC);
    }

    private void renderInfrastructure(InfrastructureSpec infra, String network, Map<String, Object> services) {
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, String> environment = new LinkedHashMap<>();

        switch (infra.type()) {
            case "postgresql" -> {
                block.put("image", "postgres:" + infra.version());
                environment.put("POSTGRES_PASSWORD", DEFAULT_DB_PASSWORD);
                environment.put("POSTGRES_USER", "gamjabox");
                environment.put("POSTGRES_DB", "gamjabox");
            }
            case "mysql" -> {
                block.put("image", "mysql:" + infra.version());
                environment.put("MYSQL_ROOT_PASSWORD", DEFAULT_DB_PASSWORD);
                environment.put("MYSQL_DATABASE", "gamjabox");
            }
            case "redis" -> block.put("image", "redis:" + infra.version());
            case "mongodb" -> block.put("image", "mongo:" + infra.version());
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        if (!environment.isEmpty()) {
            block.put("environment", environment);
        }
        block.put("restart", "unless-stopped");
        block.put("networks", List.of(network));

        boolean exposed = infra.expose() != null && infra.expose().enabled();
        if (exposed) {
            // 인프라를 외부에 노출하는 건 이례적이지만 명시적으로 요청했다면 존중함 (D-3 expose.enabled 규약)
            block.put("ports", List.of(defaultInfraPort(infra.type()) + ":" + defaultInfraPort(infra.type())));
        }

        services.put(infra.type(), block);
    }

    private int defaultInfraPort(String type) {
        return switch (type) {
            case "postgresql" -> 5432;
            case "mysql" -> 3306;
            case "redis" -> 6379;
            case "mongodb" -> 27017;
            default -> throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        };
    }
}
