package gj.cloud.ops.application.deployment.spec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// AI-Deployment-Pipeline.md 12절/18절 — 보안 경계 테스트. 이 스키마는 buildCommand/startCommand 같은
// 자유 문자열을 아예 없앴으므로(BuildRunStrategy 허용목록만 존재) 임의 셸 명령 주입은 애초에 스키마
// 레벨에서 표현 불가능하다 — 여기서는 여전히 자유 문자열로 남아있는 경로 필드(context/outputPath/artifact.path)의
// 디렉토리 탈출과 시크릿 하드코딩만 검증한다.
class DeploymentSpecPolicyValidatorTest {

    private final DeploymentSpecPolicyValidator validator = new DeploymentSpecPolicyValidator();

    private ServiceSpec serviceWithContext(String context) {
        return new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NONE, null, BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                context, new ExposeSpec(true, "http", "/"));
    }

    @Test
    void allowsNormalRelativeContext() {
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(serviceWithContext(".")), List.of(), "app-network", false);
        assertThat(validator.collectErrors(spec)).isEmpty();
    }

    @Test
    void rejectsParentDirectoryTraversalInContext() {
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(serviceWithContext("../../etc")), List.of(), "app-network", false);
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.userMessage().contains("context"));
    }

    @Test
    void rejectsAbsolutePathInContext() {
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(serviceWithContext("/etc/passwd")), List.of(), "app-network", false);
        assertThat(validator.collectErrors(spec)).isNotEmpty();
    }

    @Test
    void rejectsParentDirectoryTraversalInBuildOutputPath() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NODEJS, null, BuildRunStrategy.NPM_BUILD, "../../secrets"),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "dist"),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", new ExposeSpec(true, "http", "/"));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network", false);
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.userMessage().contains("build.outputPath"));
    }

    @Test
    void rejectsSecretLookingValueEmbeddedInSpec() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NONE, "password=hunter2", BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", new ExposeSpec(true, "http", "/"));
        DeploymentSpec spec = new DeploymentSpec("2.0", List.of(service), List.of(), "app-network", false);
        assertThat(validator.collectErrors(spec)).anyMatch(e -> e.userMessage().contains("시크릿"));
    }
}
