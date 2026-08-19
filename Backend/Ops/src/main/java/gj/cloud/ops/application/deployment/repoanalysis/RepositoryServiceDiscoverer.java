package gj.cloud.ops.application.deployment.repoanalysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class RepositoryServiceDiscoverer {

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".git", "node_modules", "dist", "build", "target", ".gradle", ".idea", ".vscode", "coverage");
    private static final int MAX_DEPTH = 10;
    private static final int MAX_SERVICES = 50;
    private static final Pattern DOCKER_FROM = Pattern.compile("(?im)^\\s*FROM\\s+");
    private static final Pattern EXPOSE_PORT = Pattern.compile("(?im)^\\s*EXPOSE\\s+(\\d{1,5})");
    private static final Pattern SERVER_PORT = Pattern.compile(
            "(?im)(?:server\\.port\\s*[=:]\\s*|port\\s*:\\s*|SERVER_PORT[:=])[^\\d]*(\\d{2,5})");

    public ServiceDiscoveryResult discover(Path repositoryRoot) {
        List<Path> directories;
        try (Stream<Path> stream = Files.walk(repositoryRoot, MAX_DEPTH)) {
            directories = stream
                    .filter(Files::isDirectory)
                    .filter(path -> !isExcluded(repositoryRoot, path))
                    .sorted(Comparator.comparing(path -> unixPath(repositoryRoot.relativize(path))))
                    .toList();
        } catch (IOException e) {
            return new ServiceDiscoveryResult(List.of(),
                    List.of("저장소의 멀티모듈 구조를 탐색하지 못했습니다."));
        }

        List<ServiceCandidate> candidates = new ArrayList<>();
        for (Path directory : directories) {
            ServiceCandidate candidate = detectCandidate(repositoryRoot, directory);
            if (candidate != null) candidates.add(candidate);
        }
        candidates = removeAggregatorCandidates(candidates);

        List<String> warnings = new ArrayList<>();
        if (candidates.size() > MAX_SERVICES) {
            warnings.add("실행 가능한 서비스 후보가 많아 상위 " + MAX_SERVICES + "개만 사용합니다.");
            candidates = candidates.subList(0, MAX_SERVICES);
        }

        Set<String> usedNames = new HashSet<>();
        List<DiscoveredService> services = new ArrayList<>();
        for (ServiceCandidate candidate : candidates) {
            String name = uniqueName(serviceName(candidate.context()), usedNames);
            services.add(new DiscoveredService(
                    name,
                    candidate.context(),
                    candidate.runtime(),
                    candidate.containerPort(),
                    candidate.portSource(),
                    true,
                    candidate.confidence(),
                    candidate.evidence()));
        }
        return new ServiceDiscoveryResult(List.copyOf(services), List.copyOf(warnings));
    }

    private ServiceCandidate detectCandidate(Path root, Path directory) {
        String context = directory.equals(root) ? "." : unixPath(root.relativize(directory));
        Path pom = directory.resolve("pom.xml");
        Path gradle = Files.exists(directory.resolve("build.gradle"))
                ? directory.resolve("build.gradle")
                : directory.resolve("build.gradle.kts");
        Path packageJson = directory.resolve("package.json");
        Path dockerfile = directory.resolve("Dockerfile");
        String dockerfileContent = read(dockerfile);

        String pomContent = read(pom);
        if (pomContent != null && isRunnableMavenModule(pomContent)) {
            PortInference port = inferPort(directory, dockerfileContent, 8080);
            return candidate(context, "java", port, "HIGH",
                    List.of("pom.xml", "Spring Boot 실행 모듈"));
        }

        String gradleContent = read(gradle);
        if (gradleContent != null && isRunnableGradleModule(gradleContent)) {
            PortInference port = inferPort(directory, dockerfileContent, 8080);
            return candidate(context, "java", port, "HIGH",
                    List.of(gradle.getFileName().toString(), "Spring Boot 실행 모듈"));
        }

        String packageContent = read(packageJson);
        if (packageContent != null && isRunnableNodePackage(packageContent)) {
            PortInference port = inferPort(directory, dockerfileContent, 3000);
            return candidate(context, "node", port, "MEDIUM",
                    List.of("package.json", "start/build 스크립트 또는 프론트엔드 런타임"));
        }

        if (isRunnablePythonModule(directory)) {
            PortInference port = inferPort(directory, dockerfileContent, 8000);
            return candidate(context, "python", port, "MEDIUM",
                    List.of("Python 의존성 파일", "웹 프레임워크 의존성"));
        }

        if (dockerfileContent != null) {
            PortInference port = inferPort(directory, dockerfileContent, 3000);
            return candidate(context, "docker", port, "MEDIUM", List.of("Dockerfile"));
        }

        if (Files.isRegularFile(directory.resolve("index.html"))
                && packageContent == null && pomContent == null && gradleContent == null) {
            return new ServiceCandidate(context, "static", 80, DiscoveredService.PORT_SOURCE_DEFAULT,
                    "HIGH", List.of("index.html", "정적 서비스 기본 포트 80"));
        }
        return null;
    }

    private ServiceCandidate candidate(
            String context,
            String runtime,
            PortInference port,
            String confidence,
            List<String> baseEvidence
    ) {
        List<String> evidence = new ArrayList<>(baseEvidence);
        evidence.add(port.evidence());
        return new ServiceCandidate(
                context, runtime, port.port(), port.source(), confidence, List.copyOf(evidence));
    }

    private List<ServiceCandidate> removeAggregatorCandidates(List<ServiceCandidate> candidates) {
        if (candidates.size() < 2) return candidates;
        Set<String> childContexts = new LinkedHashSet<>();
        for (ServiceCandidate candidate : candidates) {
            if (!".".equals(candidate.context())) childContexts.add(candidate.context());
        }
        if (childContexts.isEmpty()) return candidates;
        return candidates.stream()
                .filter(candidate -> !".".equals(candidate.context())
                        || candidate.evidence().stream().noneMatch("Dockerfile"::equals))
                .toList();
    }

    private boolean isRunnableMavenModule(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        // A parent/aggregator POM often declares the Spring Boot plugin in
        // pluginManagement for its children. It does not itself produce a
        // runnable artifact and must not become a phantom root service.
        if (normalized.matches("(?s).*<packaging>\\s*pom\\s*</packaging>.*")) {
            return false;
        }
        return normalized.contains("spring-boot-maven-plugin")
                || normalized.contains("spring-boot-starter-web")
                || normalized.contains("spring-boot-starter-webflux");
    }

    private boolean isRunnableGradleModule(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return normalized.contains("org.springframework.boot")
                && (normalized.contains("spring-boot-starter-web")
                || normalized.contains("spring-boot-starter-webflux")
                || normalized.contains("bootjar"));
    }

    private boolean isRunnableNodePackage(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        boolean hasRunnableScript = normalized.matches("(?s).*\"(?:start|start:prod|dev|build)\"\\s*:.*");
        boolean knownRuntime = normalized.contains("\"next\"")
                || normalized.contains("\"vite\"")
                || normalized.contains("\"react-scripts\"")
                || normalized.contains("\"express\"")
                || normalized.contains("\"fastify\"")
                || normalized.contains("\"nestjs");
        return hasRunnableScript && knownRuntime;
    }

    private boolean isRunnablePythonModule(Path directory) {
        String dependencies = String.join("\n",
                value(read(directory.resolve("requirements.txt"))),
                value(read(directory.resolve("pyproject.toml"))),
                value(read(directory.resolve("Pipfile")))).toLowerCase(Locale.ROOT);
        return dependencies.contains("fastapi")
                || dependencies.contains("django")
                || dependencies.contains("flask")
                || dependencies.contains("gunicorn")
                || dependencies.contains("uvicorn");
    }

    private PortInference inferPort(Path directory, String dockerfileContent, int fallback) {
        Integer dockerfilePort = inferDockerPort(dockerfileContent);
        if (dockerfilePort != null) {
            return new PortInference(
                    dockerfilePort,
                    DiscoveredService.PORT_SOURCE_DOCKERFILE_EXPOSE,
                    "Dockerfile EXPOSE " + dockerfilePort);
        }

        Integer configuredPort = inferServerPort(directory);
        if (configuredPort != null) {
            return new PortInference(
                    configuredPort,
                    DiscoveredService.PORT_SOURCE_APPLICATION_CONFIG,
                    "애플리케이션 설정 포트 " + configuredPort);
        }

        return new PortInference(
                fallback,
                DiscoveredService.PORT_SOURCE_DEFAULT,
                "런타임 기본 포트 " + fallback);
    }

    private Integer inferServerPort(Path directory) {
        List<Path> candidates = List.of(
                directory.resolve("src/main/resources/application.properties"),
                directory.resolve("src/main/resources/application.yml"),
                directory.resolve("src/main/resources/application.yaml"),
                directory.resolve(".env"),
                directory.resolve(".env.example"));
        for (Path candidate : candidates) {
            String content = read(candidate);
            if (content == null) continue;
            Matcher matcher = SERVER_PORT.matcher(content);
            if (matcher.find()) {
                Integer port = validPort(matcher.group(1));
                if (port != null) return port;
            }
        }
        return null;
    }

    private Integer inferDockerPort(String dockerfile) {
        if (dockerfile == null) return null;
        int finalStageStart = 0;
        Matcher fromMatcher = DOCKER_FROM.matcher(dockerfile);
        while (fromMatcher.find()) {
            finalStageStart = fromMatcher.start();
        }
        Matcher matcher = EXPOSE_PORT.matcher(dockerfile.substring(finalStageStart));
        if (!matcher.find()) return null;
        return validPort(matcher.group(1));
    }

    private Integer validPort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String serviceName(String context) {
        if (".".equals(context)) return "app";
        int separator = context.lastIndexOf('/');
        String raw = separator >= 0 ? context.substring(separator + 1) : context;
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "app" : normalized;
    }

    private String uniqueName(String base, Set<String> usedNames) {
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            candidate = base + "-" + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private boolean isExcluded(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (EXCLUDED_DIR_NAMES.contains(part.toString())) return true;
        }
        return false;
    }

    private String read(Path path) {
        if (path == null || !Files.isRegularFile(path)) return null;
        try {
            if (Files.size(path) > 1_048_576) return null;
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record ServiceCandidate(
            String context,
            String runtime,
            Integer containerPort,
            String portSource,
            String confidence,
            List<String> evidence
    ) {
    }

    private record PortInference(
            Integer port,
            String source,
            String evidence
    ) {
    }
}
