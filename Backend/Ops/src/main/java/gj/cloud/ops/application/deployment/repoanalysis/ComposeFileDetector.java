package gj.cloud.ops.application.deployment.repoanalysis;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class ComposeFileDetector {

    private static final Set<String> COMPOSE_FILE_NAMES = Set.of(
            "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml");
    private static final Map<String, Integer> NAME_PRIORITY = Map.of(
            "compose.yaml", 0,
            "compose.yml", 1,
            "docker-compose.yaml", 2,
            "docker-compose.yml", 3);
    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".git", "node_modules", "dist", "build", "target", ".gradle", ".idea", ".vscode", "coverage");
    private static final int MAX_SEARCH_DEPTH = 6;
    private static final int MAX_FILES = 20;
    private static final long MAX_COMPOSE_SIZE_BYTES = 1_048_576;

    public ComposeDetectionResult detect(Path repositoryRoot, String context) {
        Path searchRoot = resolveSearchRoot(repositoryRoot, context);
        String searchedContext = normalizeContext(context);
        if (!Files.isDirectory(searchRoot)) {
            return new ComposeDetectionResult(false, searchedContext, List.of(),
                    List.of("배포 디렉토리를 찾을 수 없습니다: " + searchedContext));
        }
        assertRealPathContained(repositoryRoot, searchRoot);

        List<Path> candidates;
        try (Stream<Path> stream = Files.walk(searchRoot, MAX_SEARCH_DEPTH)) {
            candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> COMPOSE_FILE_NAMES.contains(path.getFileName().toString()))
                    .filter(path -> !isExcluded(searchRoot, path))
                    .sorted(candidateComparator(searchRoot))
                    .limit(MAX_FILES + 1L)
                    .toList();
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.REPOSITORY_CLONE_FAILED);
        }

        List<String> warnings = new ArrayList<>();
        List<DetectedComposeFile> files = new ArrayList<>();
        boolean truncated = candidates.size() > MAX_FILES;
        for (Path candidate : candidates.stream().limit(MAX_FILES).toList()) {
            long size = size(candidate);
            String relativePath = unixPath(repositoryRoot.relativize(candidate));
            if (size > MAX_COMPOSE_SIZE_BYTES) {
                warnings.add(relativePath + " 파일이 1 MiB를 초과해 내용을 불러오지 않았습니다.");
                continue;
            }
            try {
                String content = Files.readString(candidate, StandardCharsets.UTF_8);
                Path parent = candidate.getParent();
                String directory = parent.equals(repositoryRoot)
                        ? "."
                        : unixPath(repositoryRoot.relativize(parent));
                files.add(new DetectedComposeFile(
                        relativePath,
                        directory,
                        content,
                        size,
                        files.isEmpty()));
            } catch (IOException e) {
                warnings.add(relativePath + " 파일을 읽지 못했습니다.");
            }
        }

        if (truncated) {
            warnings.add("Compose 후보가 많아 상위 20개만 표시합니다.");
        }
        return new ComposeDetectionResult(!files.isEmpty(), searchedContext, List.copyOf(files), List.copyOf(warnings));
    }

    private void assertRealPathContained(Path repositoryRoot, Path searchRoot) {
        try {
            if (!searchRoot.toRealPath().startsWith(repositoryRoot.toRealPath())) {
                throw new OpsException(OpsErrorCode.INVALID_PATH);
            }
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
    }

    private Path resolveSearchRoot(Path repositoryRoot, String context) {
        String normalizedContext = normalizeContext(context);
        Path resolved = ".".equals(normalizedContext)
                ? repositoryRoot.normalize()
                : repositoryRoot.resolve(normalizedContext).normalize();
        if (!resolved.startsWith(repositoryRoot.normalize())) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        return resolved;
    }

    private String normalizeContext(String context) {
        if (context == null || context.isBlank() || ".".equals(context.trim())) {
            return ".";
        }
        String normalized = context.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.equals("..") || normalized.startsWith("../")
                || normalized.contains("/../") || normalized.endsWith("/..")) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        return normalized.replaceAll("/+$", "");
    }

    private Comparator<Path> candidateComparator(Path searchRoot) {
        return Comparator
                .comparingInt((Path path) -> searchRoot.relativize(path).getNameCount())
                .thenComparingInt(path -> NAME_PRIORITY.getOrDefault(path.getFileName().toString(), 99))
                .thenComparing(path -> unixPath(searchRoot.relativize(path)));
    }

    private boolean isExcluded(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (EXCLUDED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
