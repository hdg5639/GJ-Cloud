package gj.cloud.ops.application.deployment.repoanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryServiceDiscovererTest {

    private final RepositoryServiceDiscoverer discoverer = new RepositoryServiceDiscoverer();

    @TempDir
    Path repositoryRoot;

    @Test
    void discoversRunnableMavenModulesAndSkipsSharedLibraryAndAggregatorDockerfile() throws IOException {
        Files.writeString(repositoryRoot.resolve("pom.xml"), """
                <project><packaging>pom</packaging><modules>
                  <module>common-testkit</module><module>commerce-api</module><module>community-api</module>
                </modules><build><pluginManagement><plugins><plugin>
                  <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin></plugins></pluginManagement></build></project>
                """);
        Files.writeString(repositoryRoot.resolve("Dockerfile"), "ARG MODULE\nEXPOSE 8080\n");
        createMavenModule("common-testkit", "<artifactId>spring-context</artifactId>");
        createMavenModule("commerce-api", """
                <artifactId>spring-boot-starter-web</artifactId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                """);
        createMavenModule("community-api", """
                <artifactId>spring-boot-starter-web</artifactId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                """);

        ServiceDiscoveryResult result = discoverer.discover(repositoryRoot);

        assertThat(result.services()).extracting(DiscoveredService::name)
                .containsExactly("commerce-api", "community-api");
        assertThat(result.services()).extracting(DiscoveredService::context)
                .containsExactly("commerce-api", "community-api");
        assertThat(result.services()).allSatisfy(service -> {
            assertThat(service.runtime()).isEqualTo("java");
            assertThat(service.containerPort()).isEqualTo(8080);
            assertThat(service.expose()).isTrue();
        });
    }

    @Test
    void discoversNodeAndPythonServices() throws IOException {
        Path web = Files.createDirectories(repositoryRoot.resolve("apps/web"));
        Files.writeString(web.resolve("package.json"), """
                {"scripts":{"build":"vite build"},"dependencies":{"vite":"latest"}}
                """);
        Path api = Files.createDirectories(repositoryRoot.resolve("services/api"));
        Files.writeString(api.resolve("requirements.txt"), "fastapi==1.0\nuvicorn==1.0\n");

        ServiceDiscoveryResult result = discoverer.discover(repositoryRoot);

        assertThat(result.services()).extracting(DiscoveredService::context)
                .containsExactly("apps/web", "services/api");
        assertThat(result.services()).extracting(DiscoveredService::containerPort)
                .containsExactly(3000, 8000);
    }

    @Test
    void dockerfileExposeOverridesFrameworkDefaultPort() throws IOException {
        Files.writeString(repositoryRoot.resolve("package.json"), """
                {"scripts":{"start":"next start"},"dependencies":{"next":"latest"}}
                """);
        Files.writeString(repositoryRoot.resolve("Dockerfile"), """
                FROM node:22-alpine AS builder
                EXPOSE 3000
                RUN npm run build
                FROM nginx:alpine
                EXPOSE 80/tcp
                """);

        ServiceDiscoveryResult result = discoverer.discover(repositoryRoot);

        assertThat(result.services()).singleElement().satisfies(service -> {
            assertThat(service.runtime()).isEqualTo("node");
            assertThat(service.containerPort()).isEqualTo(80);
            assertThat(service.portSource()).isEqualTo(DiscoveredService.PORT_SOURCE_DOCKERFILE_EXPOSE);
            assertThat(service.evidence()).contains("Dockerfile EXPOSE 80");
        });
    }

    private void createMavenModule(String name, String content) throws IOException {
        Path module = Files.createDirectories(repositoryRoot.resolve(name));
        Files.writeString(module.resolve("pom.xml"), "<project>" + content + "</project>");
    }
}
