package gj.cloud.ops.application.deployment.repoanalysis;

import gj.cloud.ops.application.deployment.dto.ServiceCard;
import gj.cloud.ops.application.deployment.spec.ArtifactSpec;
import gj.cloud.ops.application.deployment.spec.ArtifactType;
import gj.cloud.ops.application.deployment.spec.BuildRunStrategy;
import gj.cloud.ops.application.deployment.spec.BuildSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentMode;
import gj.cloud.ops.application.deployment.spec.ExposeSpec;
import gj.cloud.ops.application.deployment.spec.RunSpec;
import gj.cloud.ops.application.deployment.spec.RuntimeKind;
import gj.cloud.ops.application.deployment.spec.ServiceSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// AI-Deployment-Pipeline.md 4절 — AI 호출 없이도 확정할 수 있는 흔한 케이스를 결정론적으로 처리.
// 특히 "순수 정적 사이트" 케이스는 AI 호출이 아예 0회여야 함(신고된 오분류 버그의 근본 해결책) —
// index.html은 있는데 package.json/pom.xml/build.gradle/requirements.txt가 전혀 없으면 무조건 정적 사이트로
// 확정하고, AI가 nodejs 등으로 추측할 기회 자체를 주지 않는다.
@Component
public class RuleBasedSpecInferrer {

    public RuleBasedInferenceResult infer(RepositoryEvidence evidence, ServiceCard card) {
        if (evidence == null || evidence.files() == null) {
            return RuleBasedInferenceResult.unresolved(evidence,
                    List.of("저장소 분석 결과가 없습니다 (context=" + (card != null ? card.context() : "?") + ")"));
        }
        DetectedFiles files = evidence.files();

        // 4.1 — 저장소에 Dockerfile이 이미 있으면 그게 authoritative. AI에게 빌드/실행 명령을 지어내라고 하지 않는다.
        if (files.dockerfile()) {
            return resolved(card, DeploymentMode.SERVICE,
                    new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                    new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                    new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, card.containerPort()),
                    RepositoryEvidence.TYPE_DOCKERFILE, RepositoryEvidence.CONFIDENCE_HIGH,
                    List.of("Dockerfile exists"));
        }

        boolean hasBackendManifest = files.packageJson() || files.pomXml() || files.gradleBuild()
                || files.requirementsTxt() || files.pyprojectToml() || files.pipfile();

        // 4.2 — index.html은 있는데 그 외 백엔드/빌드 매니페스트가 전혀 없으면 100% 순수 정적 사이트.
        // AI 호출 0회로 확정해야 하는 케이스(신고된 버그가 바로 이 케이스를 nodejs로 잘못 분류한 것).
        if (files.indexHtml() && !hasBackendManifest) {
            return resolved(card, DeploymentMode.ARTIFACT_ONLY,
                    new BuildSpec(RuntimeKind.NONE, null, BuildRunStrategy.NONE, null),
                    new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                    new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                    RepositoryEvidence.TYPE_STATIC, RepositoryEvidence.CONFIDENCE_HIGH,
                    List.of("index.html exists", "package.json does not exist", "no backend runtime manifest was found"));
        }

        // 4.5 — Spring Boot: pom.xml/build.gradle + Spring Boot 플러그인/의존성이 있으면 확정
        if (evidence.manifest() != null && evidence.manifest().javaBuild() != null) {
            JavaBuildInfo java = evidence.manifest().javaBuild();
            if (java.springBootDetected()) {
                boolean maven = java.mavenProject();
                int javaVersion = java.javaVersion() != null ? java.javaVersion() : 21;
                return resolved(card, DeploymentMode.SERVICE,
                        new BuildSpec(RuntimeKind.JAVA, String.valueOf(javaVersion),
                                maven ? BuildRunStrategy.MAVEN_PACKAGE : BuildRunStrategy.GRADLE_BOOT_JAR, null),
                        new ArtifactSpec(ArtifactType.JAR, maven ? "target" : "build/libs"),
                        new RunSpec(RuntimeKind.JAVA, BuildRunStrategy.JAVA_JAR, card.containerPort()),
                        RepositoryEvidence.TYPE_SPRING_BOOT, RepositoryEvidence.CONFIDENCE_HIGH,
                        List.of((maven ? "pom.xml" : "build.gradle") + " exists", "Spring Boot plugin/dependency detected"));
            }
        }

        // 4.3/4.4 — Node.js: package.json이 있어야 하고, 시작 스크립트나 알려진 프레임워크 의존성처럼
        // ".js 파일이 있다"보다 강한 근거가 있어야만 nodejs로 확정. 프론트 빌드 산출물(Vite/CRA 등)은
        // 시작 스크립트가 없고 build 스크립트 + 정적 서빙 성격이면 NODE_BUILT_STATIC으로 구분.
        if (files.packageJson() && evidence.manifest() != null && evidence.manifest().packageJson() != null) {
            PackageJsonInfo pkg = evidence.manifest().packageJson();
            boolean hasStartScript = pkg.hasScript("start") || pkg.hasScript("start:prod");
            boolean hasBuildScript = pkg.hasScript("build");
            boolean looksLikeFrontendBuildTool = pkg.hasDependency("vite") || pkg.hasDependency("react-scripts")
                    || pkg.hasDependency("@angular/cli") || pkg.hasDependency("next");

            if (hasStartScript) {
                return resolved(card, DeploymentMode.SERVICE,
                        new BuildSpec(RuntimeKind.NODEJS, null,
                                hasBuildScript ? BuildRunStrategy.NPM_BUILD : BuildRunStrategy.NONE, null),
                        new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                        new RunSpec(RuntimeKind.NODEJS, BuildRunStrategy.NPM_START, card.containerPort()),
                        RepositoryEvidence.TYPE_NODEJS, RepositoryEvidence.CONFIDENCE_HIGH,
                        List.of("package.json exists", "start script exists"));
            }
            if (hasBuildScript && looksLikeFrontendBuildTool) {
                return resolved(card, DeploymentMode.ARTIFACT_ONLY,
                        new BuildSpec(RuntimeKind.NODEJS, null, BuildRunStrategy.NPM_BUILD, "dist"),
                        new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "dist"),
                        new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                        RepositoryEvidence.TYPE_NODE_BUILT_STATIC, RepositoryEvidence.CONFIDENCE_MEDIUM,
                        List.of("package.json exists", "build script exists", "frontend build tool dependency detected"));
            }
            // package.json은 있지만 시작 방법을 확정할 근거가 부족 — AI에게 넘기되, 자유 명령을 지어내게
            // 하지 않고 허용된 전략 중에서만 고르게 할 것 (3단계에서 처리)
            return RuleBasedInferenceResult.unresolved(evidence,
                    List.of("package.json은 있지만 start/build 스크립트로 실행 방식을 확정할 수 없습니다"));
        }

        // 4.6 — Python
        if (evidence.manifest() != null && evidence.manifest().python() != null) {
            PythonInfo python = evidence.manifest().python();
            if (python.fastapiDetected() || python.uvicornDetected() || python.djangoDetected() || python.flaskDetected() || python.gunicornDetected()) {
                BuildRunStrategy runStrategy = python.uvicornDetected() || python.fastapiDetected()
                        ? BuildRunStrategy.UVICORN : BuildRunStrategy.GUNICORN;
                return resolved(card, DeploymentMode.SERVICE,
                        new BuildSpec(RuntimeKind.PYTHON, "3.11", BuildRunStrategy.PIP_INSTALL, null),
                        new ArtifactSpec(ArtifactType.PYTHON_APPLICATION, "."),
                        new RunSpec(RuntimeKind.PYTHON, runStrategy, card.containerPort()),
                        RepositoryEvidence.TYPE_PYTHON, RepositoryEvidence.CONFIDENCE_MEDIUM,
                        List.of("Python dependency manifest exists", "recognized web framework dependency detected"));
            }
        }

        return RuleBasedInferenceResult.unresolved(evidence,
                List.of("결정론적 규칙으로 확정할 수 있는 근거가 부족합니다 (context=" + evidence.context() + ")"));
    }

    private RuleBasedInferenceResult resolved(ServiceCard card, DeploymentMode mode, BuildSpec build,
                                               ArtifactSpec artifact, RunSpec run, String detectedType,
                                               String confidence, List<String> positiveEvidence) {
        ExposeSpec expose = card.expose()
                ? new ExposeSpec(true, "http", run.containerPort() != null ? "/" : null)
                : null;
        ServiceSpec spec = new ServiceSpec(card.name(), mode, build, artifact, run, card.context(), expose);
        return RuleBasedInferenceResult.resolved(spec, detectedType, confidence, positiveEvidence);
    }

    public record RuleBasedInferenceResult(
            boolean resolved,
            ServiceSpec spec,
            RepositoryEvidence evidence,
            String detectedType,
            String confidence,
            List<String> positiveEvidence,
            List<String> unresolvedReasons
    ) {
        static RuleBasedInferenceResult resolved(ServiceSpec spec, String detectedType, String confidence,
                                                  List<String> positiveEvidence) {
            return new RuleBasedInferenceResult(true, spec, null, detectedType, confidence, positiveEvidence, List.of());
        }

        static RuleBasedInferenceResult unresolved(RepositoryEvidence evidence, List<String> reasons) {
            return new RuleBasedInferenceResult(false, null, evidence, RepositoryEvidence.TYPE_UNKNOWN,
                    RepositoryEvidence.CONFIDENCE_LOW, new ArrayList<>(), reasons);
        }
    }
}
