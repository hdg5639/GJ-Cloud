package gj.cloud.ops.application.deployment.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerfileGeneratorTest {

    private final DockerfileGenerator generator = new DockerfileGenerator();

    @Test
    void plainStaticSiteHasNoBuildStageAndUsesNginx() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NONE, null, BuildRunStrategy.NONE, null),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "."),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", new ExposeSpec(true, "http", "/", null));

        String dockerfile = generator.generate(service);

        assertThat(dockerfile).contains("FROM nginx:alpine");
        assertThat(dockerfile).doesNotContain("AS build");
        assertThat(dockerfile).doesNotContain("npm");
        assertThat(dockerfile).contains("EXPOSE 80");
    }

    @Test
    void nodeBuiltStaticSiteHasBuildStageThenNginxCopy() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.ARTIFACT_ONLY,
                new BuildSpec(RuntimeKind.NODEJS, "20", BuildRunStrategy.NPM_BUILD, "dist"),
                new ArtifactSpec(ArtifactType.STATIC_DIRECTORY, "dist"),
                new RunSpec(RuntimeKind.STATIC_SERVER, BuildRunStrategy.STATIC_SERVER, 80),
                ".", new ExposeSpec(true, "http", "/", null));

        String dockerfile = generator.generate(service);

        assertThat(dockerfile).contains("FROM node:20-alpine AS build");
        assertThat(dockerfile).contains("npm ci");
        assertThat(dockerfile).contains("npm run build");
        assertThat(dockerfile).contains("FROM nginx:alpine");
        assertThat(dockerfile).contains("/app/dist /usr/share/nginx/html");
    }

    // AI/사용자가 임의 문자열을 build/run 명령으로 주입할 방법이 애초에 없다는 걸 재확인 —
    // 이 생성기는 오직 enum 케이스별 고정 문자열만 다룬다(리플렉션으로 우회하지 않는 한 불가능).
    @Test
    void dockerfileStrategyThrowsSinceItShouldNeverReachGenerator() {
        ServiceSpec service = new ServiceSpec("web", DeploymentMode.SERVICE,
                new BuildSpec(RuntimeKind.DOCKERFILE, null, BuildRunStrategy.DOCKERFILE, null),
                new ArtifactSpec(ArtifactType.CONTAINER_IMAGE, "."),
                new RunSpec(RuntimeKind.DOCKERFILE, BuildRunStrategy.DOCKERFILE, 8080),
                ".", null);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> generator.generate(service))).hasMessageContaining("DOCKERFILE");
    }
}
