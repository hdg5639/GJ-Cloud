package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// gamjabox가 스펙 검증(schemaVersion 기준) 후 compose 렌더링 — D-3절 "생성 후" 단계 그대로 D-1에도 적용.
// build/artifact/run 분리 스키마(2.0) 도입 이후: 구조적 검증만 담당 — 보안/정책 검증은 별도
// DeploymentSpecPolicyValidator가 맡는다(관심사 분리, AI-Deployment-Pipeline.md 12절).
@Component
public class DeploymentSpecValidator {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("2.0");
    private static final Set<String> SUPPORTED_INFRA_TYPES = Set.of("postgresql", "mysql", "redis", "mongodb");

    private static final Set<BuildRunStrategy> NODE_BUILD_STRATEGIES = EnumSet.of(
            BuildRunStrategy.NONE, BuildRunStrategy.COPY_SOURCE,
            BuildRunStrategy.NPM_CI, BuildRunStrategy.NPM_INSTALL, BuildRunStrategy.NPM_BUILD,
            BuildRunStrategy.PNPM_INSTALL, BuildRunStrategy.PNPM_BUILD,
            BuildRunStrategy.YARN_INSTALL, BuildRunStrategy.YARN_BUILD);
    private static final Set<BuildRunStrategy> JAVA_BUILD_STRATEGIES = EnumSet.of(
            BuildRunStrategy.MAVEN_PACKAGE, BuildRunStrategy.GRADLE_BUILD, BuildRunStrategy.GRADLE_BOOT_JAR);
    private static final Set<BuildRunStrategy> PYTHON_BUILD_STRATEGIES = EnumSet.of(
            BuildRunStrategy.NONE, BuildRunStrategy.PIP_INSTALL, BuildRunStrategy.UV_SYNC);

    private static final Set<BuildRunStrategy> NODE_RUN_STRATEGIES = EnumSet.of(
            BuildRunStrategy.NONE, BuildRunStrategy.NPM_START, BuildRunStrategy.PNPM_START, BuildRunStrategy.YARN_START);
    private static final Set<BuildRunStrategy> JAVA_RUN_STRATEGIES = EnumSet.of(
            BuildRunStrategy.JAVA_JAR, BuildRunStrategy.MAVEN_SPRING_BOOT_RUN);
    private static final Set<BuildRunStrategy> PYTHON_RUN_STRATEGIES = EnumSet.of(
            BuildRunStrategy.GUNICORN, BuildRunStrategy.UVICORN);
    private static final Set<BuildRunStrategy> STATIC_RUN_STRATEGIES = EnumSet.of(
            BuildRunStrategy.STATIC_SERVER, BuildRunStrategy.NONE);

    public void validate(DeploymentSpec spec) {
        List<String> errors = collectErrors(spec);
        if (!errors.isEmpty()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
    }

    // AI 자동 재교정 — 실패 사유를 그대로 AI/사용자에게 돌려줘야 하므로 예외 대신 메시지 목록으로 반환
    public List<String> collectErrors(DeploymentSpec spec) {
        List<String> errors = new ArrayList<>();
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(spec.schemaVersion())) {
            errors.add("지원하지 않는 schemaVersion입니다: " + spec.schemaVersion());
        }
        for (ServiceSpec service : spec.services()) {
            validateService(service, errors);
        }
        if (spec.infrastructure() != null) {
            for (InfrastructureSpec infra : spec.infrastructure()) {
                if (!SUPPORTED_INFRA_TYPES.contains(infra.type())) {
                    errors.add("지원하지 않는 infrastructure type입니다: " + infra.type());
                }
            }
        }
        return errors;
    }

    private void validateService(ServiceSpec service, List<String> errors) {
        String svc = service.name();

        // build.runtime과 build.strategy 조합 일관성 (7절/11.1절 — 기계적 오류)
        boolean buildStrategyValid = switch (service.build().runtime()) {
            case NODEJS -> NODE_BUILD_STRATEGIES.contains(service.build().strategy());
            case JAVA -> JAVA_BUILD_STRATEGIES.contains(service.build().strategy());
            case PYTHON -> PYTHON_BUILD_STRATEGIES.contains(service.build().strategy());
            case NONE, STATIC_SERVER ->
                    service.build().strategy() == BuildRunStrategy.NONE || service.build().strategy() == BuildRunStrategy.COPY_SOURCE;
            case DOCKERFILE -> service.build().strategy() == BuildRunStrategy.DOCKERFILE;
        };
        if (!buildStrategyValid) {
            errors.add("build.strategy가 build.runtime과 맞지 않습니다: " + service.build().runtime() + "/" + service.build().strategy()
                    + " (service=" + svc + ")");
        }

        // run.runtime과 run.strategy 조합 일관성
        boolean runStrategyValid = switch (service.run().runtime()) {
            case NODEJS -> NODE_RUN_STRATEGIES.contains(service.run().strategy());
            case JAVA -> JAVA_RUN_STRATEGIES.contains(service.run().strategy());
            case PYTHON -> PYTHON_RUN_STRATEGIES.contains(service.run().strategy());
            case STATIC_SERVER -> STATIC_RUN_STRATEGIES.contains(service.run().strategy());
            case NONE -> service.run().strategy() == BuildRunStrategy.NONE;
            case DOCKERFILE -> true; // 저장소 자체 Dockerfile의 ENTRYPOINT/CMD를 그대로 신뢰
        };
        if (!runStrategyValid) {
            errors.add("run.strategy가 run.runtime과 맞지 않습니다: " + service.run().runtime() + "/" + service.run().strategy()
                    + " (service=" + svc + ")");
        }

        // artifact.type과 run 조합 일관성 — STATIC_DIRECTORY인데 애플리케이션 서버 전략을 쓰면 안 됨 등
        boolean artifactConsistent = switch (service.artifact().type()) {
            case STATIC_DIRECTORY -> STATIC_RUN_STRATEGIES.contains(service.run().strategy());
            case JAR -> JAVA_RUN_STRATEGIES.contains(service.run().strategy());
            case PYTHON_APPLICATION -> PYTHON_RUN_STRATEGIES.contains(service.run().strategy());
            case CONTAINER_IMAGE -> true;
            case UNKNOWN -> false;
        };
        if (!artifactConsistent) {
            errors.add("artifact.type과 run 전략이 맞지 않습니다: " + service.artifact().type() + "/" + service.run().strategy()
                    + " (service=" + svc + ")");
        }

        // expose 일관성 (7절 — 존재하지 않을 필드를 지어내지 않기: expose=false인데 healthCheckPath가 있으면 오류)
        if (service.expose() != null) {
            ExposeSpec expose = service.expose();
            if (!expose.enabled() && expose.healthCheckPath() != null && !expose.healthCheckPath().isBlank()) {
                errors.add("expose.enabled=false인데 healthCheckPath가 지정돼 있습니다 (service=" + svc + ")");
            }
            if (expose.enabled() && !"http".equalsIgnoreCase(expose.protocol())
                    && expose.healthCheckPath() != null && !expose.healthCheckPath().isBlank()) {
                errors.add("HTTP가 아닌 노출에는 healthCheckPath를 지정할 수 없습니다 (service=" + svc + ")");
            }
            if (expose.enabled() && service.run().containerPort() == null) {
                errors.add("expose.enabled=true인데 run.containerPort가 없습니다 (service=" + svc + ")");
            }
        }

        // 포트 범위
        Integer port = service.run().containerPort();
        if (port != null && (port < 1 || port > 65535)) {
            errors.add("run.containerPort가 유효 범위를 벗어났습니다: " + port + " (service=" + svc + ")");
        }

        // artifact-only 모드에서는 실제 애플리케이션 서버 전략을 쓸 수 없음 (정적 서빙 또는 없음만 허용)
        if (service.deploymentMode() == DeploymentMode.ARTIFACT_ONLY
                && !STATIC_RUN_STRATEGIES.contains(service.run().strategy())) {
            errors.add("deploymentMode=ARTIFACT_ONLY인 서비스는 STATIC_SERVER 또는 NONE run 전략만 가능합니다 (service=" + svc + ")");
        }
    }
}
