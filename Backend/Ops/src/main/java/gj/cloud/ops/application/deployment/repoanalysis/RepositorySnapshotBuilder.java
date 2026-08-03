package gj.cloud.ops.application.deployment.repoanalysis;

import gj.cloud.ops.application.deployment.git.GitCloneSecurityValidator;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.process.LocalCommandExecutor;
import gj.cloud.ops.global.process.LocalCommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// AI-Deployment-Pipeline.md 3절 — AI 호출 전에 저장소를 실제로 얕게 클론해 결정론적 증거를 수집한다.
// 실제 배포 시점 체크아웃(GitReleaseManager, VM 위 SSH 세션)과는 완전히 별개 — 이건 스펙 "생성" 시점에
// Ops 컨테이너 자신의 로컬 프로세스로 수행되고, 분석이 끝나면 클론을 즉시 삭제한다(저장소 원문 비영속화).
@Slf4j
@Component
@RequiredArgsConstructor
public class RepositorySnapshotBuilder {

    // GitReleaseManager와 동일한 검증 규칙 — 스킴 화이트리스트(ext:: 등 임의 명령 실행 벡터 차단),
    // 브랜치명 셸/옵션 인젝션 차단. 기존 클래스를 건드리지 않기 위해 의도적으로 중복 정의함.
    private static final Pattern SAFE_REPO_URL = Pattern.compile("^https://[A-Za-z0-9._/:@-]+$");
    private static final Pattern SAFE_BRANCH_NAME = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".git", "node_modules", "dist", "build", "target", ".gradle", ".idea", ".vscode", "coverage");

    private final LocalCommandExecutor localCommandExecutor;
    private final ManifestParser manifestParser;
    private final GitCloneSecurityValidator gitCloneSecurityValidator;
    private final ComposeFileDetector composeFileDetector;
    private final RepositoryServiceDiscoverer repositoryServiceDiscoverer;

    @Value("${ops.repo-analysis.clone-timeout-ms:60000}")
    private long cloneTimeoutMs;
    @Value("${ops.repo-analysis.max-files:2000}")
    private int maxFiles;
    @Value("${ops.repo-analysis.max-depth:12}")
    private int maxDepth;
    @Value("${ops.repo-analysis.max-file-size-bytes:1048576}")
    private long maxFileSizeBytes;
    @Value("${ops.repo-analysis.max-total-size-bytes:52428800}")
    private long maxTotalSizeBytes;
    @Value("${ops.repo-analysis.max-clone-size-bytes:268435456}")
    private long maxCloneSizeBytes;
    @Value("${ops.git.local-egress-proxy-url:}")
    private String gitLocalEgressProxyUrl;
    @Value("${ops.repo-analysis.max-memory-bytes:536870912}")
    private long maxCloneMemoryBytes;
    @Value("${ops.repo-analysis.max-cpu-seconds:60}")
    private long maxCloneCpuSeconds;
    @Value("${ops.repo-analysis.max-processes:64}")
    private long maxCloneProcesses;

    public Map<String, RepositoryEvidence> analyze(String repoUrl, String branch, String patToken, List<String> contexts) {
        return withClonedRepository(repoUrl, branch, patToken, repositoryRoot -> {
            Map<String, RepositoryEvidence> evidenceByContext = new LinkedHashMap<>();
            for (String context : contexts) {
                evidenceByContext.put(context, buildEvidence(repositoryRoot, context));
            }
            return evidenceByContext;
        });
    }

    public ComposeDetectionResult detectCompose(String repoUrl, String branch, String patToken, String context) {
        return withClonedRepository(repoUrl, branch, patToken, repositoryRoot -> {
            ComposeDetectionResult detection = composeFileDetector.detect(repositoryRoot, context);
            ServiceDiscoveryResult discovery = repositoryServiceDiscoverer.discover(repositoryRoot);
            List<String> warnings = new ArrayList<>(detection.warnings());
            warnings.addAll(discovery.warnings());
            return new ComposeDetectionResult(
                    detection.detected(),
                    detection.searchedContext(),
                    detection.files(),
                    discovery.services(),
                    List.copyOf(warnings));
        });
    }

    public ServiceDiscoveryResult discoverServices(String repoUrl, String branch, String patToken) {
        return withClonedRepository(repoUrl, branch, patToken, repositoryServiceDiscoverer::discover);
    }

    private <T> T withClonedRepository(
            String repoUrl,
            String branch,
            String patToken,
            Function<Path, T> operation
    ) {
        validateRepoUrl(repoUrl);
        validateBranch(branch);
        gitCloneSecurityValidator.assertHostNotInternal(repoUrl);

        Path tempDir = null;
        Path askpassScript = null;
        try {
            tempDir = Files.createTempDirectory("gj-repo-analysis-");
            askpassScript = patToken != null && !patToken.isBlank() ? writeAskpassScript(patToken) : null;

            // DEP-001: 최초 연결 시 사전 DNS 검증을 통과해도, 서버가 응답에서 내부 주소로 리다이렉트하면
            // 그 우회 경로로 SSRF가 가능하므로 리다이렉트 자체를 비활성화
            List<String> cloneCommand = List.of(
                    "prlimit",
                    "--as=" + maxCloneMemoryBytes,
                    "--fsize=" + maxCloneSizeBytes,
                    "--cpu=" + maxCloneCpuSeconds,
                    "--nproc=" + maxCloneProcesses,
                    "--nofile=256",
                    "--",
                    "git", "-c", "http.followRedirects=false", "clone", "--depth", "1", "--single-branch",
                    "--filter=blob:none", "--no-tags", "--branch", branch, repoUrl, tempDir.toString());
            Map<String, String> env = new LinkedHashMap<>();
            env.put("GIT_TERMINAL_PROMPT", "0");
            if (askpassScript != null) {
                env.put("GIT_ASKPASS", askpassScript.toString());
            }
            if (gitLocalEgressProxyUrl != null && !gitLocalEgressProxyUrl.isBlank()) {
                validateProxyUrl(gitLocalEgressProxyUrl);
                env.put("HTTPS_PROXY", gitLocalEgressProxyUrl);
                env.put("https_proxy", gitLocalEgressProxyUrl);
            }

            LocalCommandResult result = localCommandExecutor.exec(
                    cloneCommand, null, env, cloneTimeoutMs, tempDir.toFile(), maxCloneSizeBytes);
            if (!result.isSuccess()) {
                log.warn("저장소 분석용 클론 실패: repoUrl={}, branch={}, stderr={}", repoUrl, branch, trim(result.stderr()));
                throw new OpsException(OpsErrorCode.REPOSITORY_CLONE_FAILED);
            }
            if (directorySize(tempDir) > maxCloneSizeBytes) {
                throw new OpsException(OpsErrorCode.REPOSITORY_TOO_LARGE);
            }

            return operation.apply(tempDir);
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.REPOSITORY_CLONE_FAILED);
        } finally {
            if (askpassScript != null) {
                deleteQuietly(askpassScript);
            }
            if (tempDir != null) {
                deleteRecursivelyQuietly(tempDir);
            }
        }
    }

    private RepositoryEvidence buildEvidence(Path repoRoot, String context) {
        Path contextDir = resolveContext(repoRoot, context);
        if (!Files.isDirectory(contextDir)) {
            return new RepositoryEvidence(context, null, null, RepositoryEvidence.TYPE_UNKNOWN,
                    RepositoryEvidence.CONFIDENCE_LOW, List.of(),
                    List.of("context 디렉토리를 찾을 수 없습니다: " + context), List.of(), null);
        }

        List<Path> files = walkBounded(contextDir);

        boolean dockerfile = Files.exists(contextDir.resolve("Dockerfile"));
        boolean composeFile = existsAny(contextDir, "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml");
        boolean packageJson = Files.exists(contextDir.resolve("package.json"));
        boolean pomXml = Files.exists(contextDir.resolve("pom.xml"));
        boolean gradleBuild = existsAny(contextDir, "build.gradle", "build.gradle.kts");
        boolean requirementsTxt = Files.exists(contextDir.resolve("requirements.txt"));
        boolean pyprojectToml = Files.exists(contextDir.resolve("pyproject.toml"));
        boolean pipfile = Files.exists(contextDir.resolve("Pipfile"));
        boolean indexHtml = Files.exists(contextDir.resolve("index.html"));

        DetectedFiles detectedFiles = new DetectedFiles(dockerfile, composeFile, packageJson, pomXml,
                gradleBuild, requirementsTxt, pyprojectToml, pipfile, indexHtml);

        PackageJsonInfo packageJsonInfo = packageJson ? manifestParser.parsePackageJson(contextDir.resolve("package.json")) : null;
        JavaBuildInfo javaBuildInfo = (pomXml || gradleBuild) ? manifestParser.parseJavaBuild(contextDir) : null;
        PythonInfo pythonInfo = (requirementsTxt || pyprojectToml || pipfile) ? manifestParser.parsePython(contextDir) : null;
        ManifestData manifest = new ManifestData(packageJsonInfo, javaBuildInfo, pythonInfo);

        String evidenceHash = computeEvidenceHash(files, detectedFiles);

        return new RepositoryEvidence(context, detectedFiles, manifest,
                RepositoryEvidence.TYPE_UNKNOWN, RepositoryEvidence.CONFIDENCE_LOW, List.of(), List.of(), List.of(), evidenceHash);
    }

    private Path resolveContext(Path repoRoot, String context) {
        String cleaned = context == null || context.isBlank() || context.equals(".") ? "" : context;
        cleaned = cleaned.startsWith("./") ? cleaned.substring(2) : cleaned;
        cleaned = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        if (cleaned.contains("..")) {
            throw new OpsException(OpsErrorCode.INVALID_PATH);
        }
        return cleaned.isEmpty() ? repoRoot : repoRoot.resolve(cleaned).normalize();
    }

    private List<Path> walkBounded(Path contextDir) {
        List<Path> result = new ArrayList<>();
        long[] totalSize = {0};
        try (Stream<Path> stream = Files.walk(contextDir, maxDepth)) {
            stream.forEach(path -> {
                if (result.size() >= maxFiles || totalSize[0] >= maxTotalSizeBytes) {
                    return;
                }
                if (isExcluded(contextDir, path)) {
                    return;
                }
                if (Files.isRegularFile(path)) {
                    long size = sizeQuietly(path);
                    if (size > maxFileSizeBytes) {
                        return;
                    }
                    totalSize[0] += size;
                    result.add(path);
                }
            });
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.REPOSITORY_TOO_LARGE);
        }
        return result;
    }

    private boolean isExcluded(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (EXCLUDED_DIR_NAMES.contains(name) || name.startsWith(".env")) {
                return true;
            }
        }
        return false;
    }

    private long sizeQuietly(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private long directorySize(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            long total = 0L;
            Iterator<Path> files = stream.filter(Files::isRegularFile).iterator();
            while (files.hasNext()) {
                Path path = files.next();
                long size = Files.size(path);
                if (size > maxCloneSizeBytes - total) {
                    return maxCloneSizeBytes + 1;
                }
                total += size;
            }
            return total;
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.REPOSITORY_CLONE_FAILED);
        }
    }

    private boolean existsAny(Path dir, String... names) {
        for (String name : names) {
            if (Files.exists(dir.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private String computeEvidenceHash(List<Path> files, DetectedFiles detectedFiles) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> sortedNames = files.stream().map(Path::toString).sorted().toList();
            for (String name : sortedNames) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
            }
            digest.update(detectedFiles.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private void validateRepoUrl(String repoUrl) {
        if (repoUrl == null || !SAFE_REPO_URL.matcher(repoUrl).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    private void validateBranch(String branch) {
        if (branch == null || !SAFE_BRANCH_NAME.matcher(branch).matches() || branch.startsWith("-")) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    private void validateProxyUrl(String proxyUrl) {
        try {
            java.net.URI uri = java.net.URI.create(proxyUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
            }
        } catch (IllegalArgumentException e) {
            throw new OpsException(OpsErrorCode.INVALID_REPO_CONFIG);
        }
    }

    // 로컬 GIT_ASKPASS 스크립트 — GitReleaseManager.withAskpass()와 동일한 기법을 로컬 프로세스용으로
    // 적용: PAT는 명령줄 인자에 절대 나타나지 않고(ps aux 노출 방지) 파일 내용으로만 전달, 사용 직후 삭제.
    private Path writeAskpassScript(String patToken) throws IOException {
        String escapedPat = patToken.replace("'", "'\\''");
        String content = "#!/bin/sh\n"
                + "case \"$1\" in\n"
                + "  Username*) echo 'oauth2' ;;\n"
                + "  *) echo '" + escapedPat + "' ;;\n"
                + "esac\n";
        Path script = Files.createTempFile("gj-askpass-" + UUID.randomUUID(), ".sh");
        Files.writeString(script, content, StandardCharsets.UTF_8);
        if (!script.toFile().setExecutable(true, true)) {
            log.warn("askpass 스크립트 실행 권한 설정 실패: {}", script);
        }
        return script;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일 삭제 실패는 치명적이지 않음 (컨테이너 재시작 시 정리됨)
        }
    }

    private void deleteRecursivelyQuietly(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // 저장소 원문을 남기지 않는 게 목표이므로 최선을 다해 정리하되, 실패해도 예외로 흐름을 막지 않음
        }
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
