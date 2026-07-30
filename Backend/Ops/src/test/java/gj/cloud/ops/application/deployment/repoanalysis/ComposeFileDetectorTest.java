package gj.cloud.ops.application.deployment.repoanalysis;

import gj.cloud.ops.global.exception.OpsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComposeFileDetectorTest {

    private final ComposeFileDetector detector = new ComposeFileDetector();

    @TempDir
    Path repositoryRoot;

    @Test
    void findsAndPrioritizesNearestModernComposeFile() throws IOException {
        Files.writeString(repositoryRoot.resolve("docker-compose.yml"), "services:\n  legacy: {}\n");
        Files.writeString(repositoryRoot.resolve("compose.yaml"), "services:\n  app: {}\n");
        Files.createDirectories(repositoryRoot.resolve("apps/api"));
        Files.writeString(repositoryRoot.resolve("apps/api/compose.yml"), "services:\n  api: {}\n");

        ComposeDetectionResult result = detector.detect(repositoryRoot, null);

        assertThat(result.detected()).isTrue();
        assertThat(result.files()).extracting(DetectedComposeFile::path)
                .containsExactly("compose.yaml", "docker-compose.yml", "apps/api/compose.yml");
        assertThat(result.files().get(0).primary()).isTrue();
        assertThat(result.files().get(0).directory()).isEqualTo(".");
    }

    @Test
    void searchesWholeRepositoryButPrioritizesSelectedDeploymentContext() throws IOException {
        Files.writeString(repositoryRoot.resolve("compose.yaml"), "services: {}\n");
        Files.createDirectories(repositoryRoot.resolve("apps/api"));
        Files.writeString(repositoryRoot.resolve("apps/api/docker-compose.yaml"), "services:\n  api: {}\n");

        ComposeDetectionResult result = detector.detect(repositoryRoot, "apps/api");

        assertThat(result.files()).hasSize(2);
        assertThat(result.files().get(0).path()).isEqualTo("apps/api/docker-compose.yaml");
        assertThat(result.files().get(0).directory()).isEqualTo("apps/api");
        assertThat(result.files().get(1).path()).isEqualTo("compose.yaml");
        assertThat(result.searchedContext()).isEqualTo("apps/api");
    }

    @Test
    void findsEnvironmentSpecificComposeFileNames() throws IOException {
        Files.writeString(repositoryRoot.resolve("compose.prod.yml"), "services:\n  app: {}\n");
        Files.writeString(repositoryRoot.resolve("docker-compose.local.yaml"), "services:\n  local: {}\n");

        ComposeDetectionResult result = detector.detect(repositoryRoot, null);

        assertThat(result.files()).extracting(DetectedComposeFile::path)
                .containsExactly("compose.prod.yml", "docker-compose.local.yaml");
    }

    @Test
    void fallsBackToRepositoryRootWhenPreferredContextDoesNotExist() throws IOException {
        Files.writeString(repositoryRoot.resolve("docker-compose.yml"), "services:\n  app: {}\n");

        ComposeDetectionResult result = detector.detect(repositoryRoot, "missing/module");

        assertThat(result.detected()).isTrue();
        assertThat(result.files().get(0).path()).isEqualTo("docker-compose.yml");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("저장소 전체"));
    }

    @Test
    void rejectsPathTraversalContext() {
        assertThatThrownBy(() -> detector.detect(repositoryRoot, "../secret"))
                .isInstanceOf(OpsException.class);
    }
}
