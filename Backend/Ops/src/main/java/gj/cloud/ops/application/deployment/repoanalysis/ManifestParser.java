package gj.cloud.ops.application.deployment.repoanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// package.json/pom.xml/build.gradle/requirements.txt/pyproject.toml/Pipfile을 결정론적으로 파싱해
// RepositoryEvidence의 ManifestData를 채운다. TOML 파서가 클래스패스에 없어 pyproject.toml/Pipfile은
// 필요한 몇 개 키만 정규식으로 뽑아내는 최소 구현 — 완전한 TOML 파싱이 아니다.
@RequiredArgsConstructor
@Component
public class ManifestParser {

    private final ObjectMapper objectMapper;

    private static final Pattern SPRING_BOOT_PLUGIN =
            Pattern.compile("org\\.springframework\\.boot|spring-boot-starter|spring-boot-gradle-plugin");
    private static final Pattern JAVA_VERSION_PROPERTY =
            Pattern.compile("(?:sourceCompatibility|java\\.version|<java\\.version>)\\s*[=:]?\\s*['\"]?(\\d{1,2})");
    private static final Pattern TOML_KEY_VALUE = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*\"?([^\"\\n]*)\"?\\s*$");

    public PackageJsonInfo parsePackageJson(Path file) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(file));
            Map<String, String> scripts = new HashMap<>();
            root.path("scripts").properties().forEach(e -> scripts.put(e.getKey(), e.getValue().asText()));

            Set<String> deps = collectKeys(root.path("dependencies"));
            Set<String> devDeps = collectKeys(root.path("devDependencies"));

            Map<String, String> engines = new HashMap<>();
            root.path("engines").properties().forEach(e -> engines.put(e.getKey(), e.getValue().asText()));

            String packageManager = root.path("packageManager").isMissingNode() ? null : root.path("packageManager").asText();

            return new PackageJsonInfo(
                    root.path("name").isMissingNode() ? null : root.path("name").asText(),
                    root.path("private").asBoolean(false),
                    root.path("type").isMissingNode() ? null : root.path("type").asText(),
                    scripts,
                    deps,
                    devDeps,
                    root.has("workspaces"),
                    packageManager,
                    engines
            );
        } catch (IOException e) {
            return null;
        }
    }

    public JavaBuildInfo parseJavaBuild(Path context) {
        Path pomXml = context.resolve("pom.xml");
        Path gradle = context.resolve("build.gradle");
        Path gradleKts = context.resolve("build.gradle.kts");
        Path settingsGradle = context.resolve("settings.gradle");
        Path settingsGradleKts = context.resolve("settings.gradle.kts");

        boolean maven = Files.exists(pomXml);
        boolean gradleProject = Files.exists(gradle) || Files.exists(gradleKts);
        if (!maven && !gradleProject) {
            return null;
        }

        String content = readQuietly(maven ? pomXml : (Files.exists(gradle) ? gradle : gradleKts));
        boolean springBoot = content != null && SPRING_BOOT_PLUGIN.matcher(content).find();
        boolean multiModule = Files.exists(settingsGradle) || Files.exists(settingsGradleKts)
                || (content != null && content.contains("<modules>"));

        Integer javaVersion = null;
        if (content != null) {
            Matcher m = JAVA_VERSION_PROPERTY.matcher(content);
            if (m.find()) {
                try {
                    javaVersion = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // 버전 파싱 실패 시 그냥 null로 둠 — 추론 단계에서 기본값을 사용
                }
            }
        }

        String packaging = content != null && content.contains("<packaging>war</packaging>") ? "war" : "jar";
        return new JavaBuildInfo(maven, gradleProject, springBoot, multiModule, javaVersion, packaging);
    }

    public PythonInfo parsePython(Path context) {
        Set<String> deps = new HashSet<>();
        appendRequirementsTxtDeps(context.resolve("requirements.txt"), deps);
        appendTomlDeps(context.resolve("pyproject.toml"), deps);
        appendTomlDeps(context.resolve("Pipfile"), deps);

        if (deps.isEmpty()) {
            return null;
        }
        boolean fastapi = containsIgnoreCase(deps, "fastapi");
        boolean django = containsIgnoreCase(deps, "django");
        boolean flask = containsIgnoreCase(deps, "flask");
        boolean gunicorn = containsIgnoreCase(deps, "gunicorn");
        boolean uvicorn = containsIgnoreCase(deps, "uvicorn");
        return new PythonInfo(fastapi, django, flask, gunicorn, uvicorn);
    }

    private void appendRequirementsTxtDeps(Path file, Set<String> out) {
        String content = readQuietly(file);
        if (content == null) {
            return;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String name = trimmed.split("[=<>~!;\\[]")[0].trim();
            if (!name.isEmpty()) {
                out.add(name.toLowerCase());
            }
        }
    }

    // pyproject.toml/Pipfile 최소 파싱 — 전체 TOML 문법을 지원하지 않고, "이름 = ..." 형태의 의존성 줄만
    // 느슨하게 인식한다 (테이블 헤더 [tool.poetry.dependencies] 등은 무시하고 파일 전체에서 후보를 수집).
    private void appendTomlDeps(Path file, Set<String> out) {
        String content = readQuietly(file);
        if (content == null) {
            return;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                continue;
            }
            Matcher m = TOML_KEY_VALUE.matcher(trimmed);
            if (m.matches()) {
                out.add(m.group(1).toLowerCase());
            }
        }
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(target));
    }

    private Set<String> collectKeys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(keys::add);
        return keys;
    }

    private String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
