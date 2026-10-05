package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.application.deployment.dto.*;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic editor/pre-deployment contract. Never sends values to AI or writes to a VM. */
@Service
@RequiredArgsConstructor
public class ComposePreparationService {
    private final ComposeValidator validator;
    private static final Pattern SAFE_PATH = Pattern.compile("^[a-zA-Z0-9_./-]+$");

    public ComposePreparationResult inspect(ComposePreparationRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<EnvironmentFile> uploads = new ArrayList<>();
        List<HealthCheck> checks = new ArrayList<>(request.healthChecks() == null ? List.of() : request.healthChecks());
        List<ComposePreparationResult.Service> summaries = new ArrayList<>();
        Map<String, Object> root;
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(request.composeContent());
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException();
            root = map(parsed);
        } catch (Exception e) {
            return new ComposePreparationResult(false, request.composeContent(), List.of(), checks, summaries,
                    List.of("YAML 문법을 확인해주세요. 최상위는 services를 포함한 객체여야 합니다."), List.of());
        }
        Map<String, Object> services = map(root.get("services"));
        if (services.isEmpty()) errors.add("배포할 services가 없습니다.");
        String context;
        try { context = relativePath(request.context() == null || request.context().isBlank() ? "." : request.context()); }
        catch (IllegalArgumentException e) { context = "."; errors.add("배포 디렉터리는 저장소 내부의 상대경로여야 합니다."); }
        boolean changed = false;
        if (request.caddyfileOverride() != null) {
            Map<String, Object> router = map(services.get("gamjabox-router"));
            boolean managed = "true".equals(String.valueOf(map(router.get("labels")).get("gamjabox.router.managed")));
            if (router.get("configs") instanceof List<?> mounts) {
                managed |= mounts.stream().anyMatch(mount -> mount instanceof Map<?, ?> config
                        && "/etc/caddy/Caddyfile".equals(config.get("target"))
                        && String.valueOf(map(map(root.get("configs")).get(String.valueOf(config.get("source")))).get("content")).contains("@gamjabox_health"));
            }
            if (managed && router.get("configs") instanceof List<?> configs) {
                Map<String, Object> labels = map(router.get("labels"));
                labels.put("gamjabox.router.managed", "true");
                router.put("labels", labels);
                for (Object mount : configs) {
                    if (mount instanceof Map<?, ?> config && "/etc/caddy/Caddyfile".equals(config.get("target"))) {
                        map(root.get("configs")).put(String.valueOf(config.get("source")), Map.of("content", request.caddyfileOverride()));
                        changed = true;
                    }
                }
            } else errors.add("직접 편집은 GamjaBox가 생성한 Caddy 설정에만 적용할 수 있습니다.");
        }
        Set<String> uploadPaths = new HashSet<>();
        for (EnvironmentFile file : request.environmentFiles() == null ? List.<EnvironmentFile>of() : request.environmentFiles()) {
            if (file == null || file.vmPath() == null || file.content() == null) {
                errors.add("환경변수 파일의 경로와 내용을 입력해주세요."); continue;
            }
            // Old records already contain release-relative upload paths and explicit env_file YAML.
            if (file.serviceNames() == null) { uploads.add(file); continue; }
            String envPath;
            try { envPath = relativePath(file.vmPath()); if (envPath.equals(".")) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException e) { errors.add("환경변수 파일은 Compose 기준의 안전한 상대경로여야 합니다."); continue; }
            String uploadPath = context.equals(".") ? envPath : context + "/" + envPath;
            if (!uploadPaths.add(uploadPath)) { errors.add("동일한 환경변수 파일 경로가 중복됩니다: " + envPath); continue; }
            uploads.add(new EnvironmentFile(uploadPath, file.content()));
            Set<String> keys = environmentKeys(file.content());
            for (Object definition : services.values()) {
                Map<String, Object> service = map(definition);
                Object existing = service.get("env_file");
                List<?> paths = existing instanceof List<?> list ? list : existing == null ? List.of() : List.of(existing);
                List<?> retained = paths.stream().filter(path -> !envPath.equals(path)
                        && !(path instanceof Map<?, ?> entry && envPath.equals(entry.get("path")))).toList();
                if (retained.size() != paths.size()) {
                    if (retained.isEmpty()) service.remove("env_file"); else service.put("env_file", retained);
                    changed = true;
                }
            }
            if (keys.isEmpty() && !file.content().isBlank()) warnings.add(envPath + ": KEY=value 형식의 환경변수를 확인해주세요.");
            for (String name : file.serviceNames()) {
                if (!services.containsKey(name)) { errors.add("환경변수를 받을 서비스가 없습니다: " + name); continue; }
                Map<String, Object> service = map(services.get(name));
                List<Object> paths = new ArrayList<>();
                Object existing = service.get("env_file");
                if (existing instanceof List<?> list) paths.addAll(list);
                else if (existing != null) paths.add(existing);
                if (paths.stream().noneMatch(path -> envPath.equals(path) || path instanceof Map<?, ?> entry && envPath.equals(entry.get("path")))) paths.add(envPath);
                service.put("env_file", paths);
                changed = true;
                for (String key : keys) if (environmentNames(service.get("environment")).contains(key)) {
                    warnings.add(name + ": " + key + "는 Compose environment가 파일보다 우선합니다. environment 값을 수정하거나 제거하세요.");
                }
            }
            if (file.serviceNames().isEmpty() && !envPath.equals(".env")) warnings.add(envPath + ": 서비스에 연결되지 않았습니다. Compose 치환용 기본 파일은 .env입니다.");
        }
        Map<String, String> interpolation = new LinkedHashMap<>();
        for (EnvironmentFile file : uploads) {
            String defaultPath = context.equals(".") ? ".env" : context + "/.env";
            if (defaultPath.equals(file.vmPath())) {
                for (String line : file.content().split("\\R")) {
                    var assignment = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$").matcher(line);
                    if (!assignment.matches()) continue;
                    String value = assignment.group(2).strip();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) value = value.substring(1, value.length()-1);
                    else value = value.replaceFirst("\\s+#.*$", "").stripTrailing();
                    interpolation.put(assignment.group(1), value);
                }
            }
        }
        Map<String, Object> analyzedServices = map(resolveValues(services, interpolation, errors));
        checks.removeIf(check -> Boolean.TRUE.equals(check.readinessOnly()) && !services.containsKey(check.serviceName()));
        for (var entry : analyzedServices.entrySet()) {
            String name = entry.getKey(); Map<String, Object> service = map(entry.getValue());
            if (service.isEmpty()) { errors.add(name + ": 서비스 설정이 객체여야 합니다."); continue; }
            if (!service.containsKey("image") && !service.containsKey("build")) errors.add(name + ": image 또는 build가 필요합니다.");
            Object dependencies = service.get("depends_on");
            Collection<?> names = dependencies instanceof Map<?, ?> deps ? deps.keySet() : dependencies instanceof List<?> deps ? deps : List.of();
            for (Object dependency : names) if (!services.containsKey(String.valueOf(dependency))) errors.add(name + ": depends_on 서비스가 없습니다: " + dependency);
            List<Binding> bindings = bindings(service);
            Set<Integer> internal = new LinkedHashSet<>();
            bindings.forEach(b -> internal.add(b.target));
            if (service.get("expose") instanceof List<?> expose) expose.forEach(port -> { Integer number = number(port); if (number != null) internal.add(number); });
            summaries.add(new ComposePreparationResult.Service(name, List.copyOf(internal), bindings.stream().map(b -> b.host).distinct().toList(), List.copyOf(environmentNames(service.get("environment")))));
            if (!service.containsKey("profiles") && checks.stream().noneMatch(check -> name.equals(check.serviceName()) && Boolean.TRUE.equals(check.readinessOnly()))) {
                boolean completedDependency = analyzedServices.values().stream().anyMatch(definition -> {
                    Map<String, Object> dependency = map(map(map(definition).get("depends_on")).get(name));
                    return "service_completed_successfully".equals(dependency.get("condition"));
                });
                checks.add(new HealthCheck(name, "", null, completedDependency || internal.isEmpty() ? null : internal.iterator().next(), true, completedDependency));
            }
        }
        for (ExposedRoute route : request.exposedRoutes() == null ? List.<ExposedRoute>of() : request.exposedRoutes()) {
            if (!analyzedServices.containsKey(route.serviceName())) { errors.add("공개 주소의 서비스가 Compose에 없습니다: " + route.serviceName()); continue; }
            if (!Set.of("HTTP", "TCP").contains(route.protocol())) errors.add("주소 연결 프로토콜은 HTTP 또는 TCP여야 합니다.");
            List<Binding> direct = bindings(map(analyzedServices.get(route.serviceName()))).stream().filter(b -> b.host == route.port()).toList();
            if (direct.isEmpty()) {
                boolean throughRouter = analyzedServices.entrySet().stream().anyMatch(entry ->
                        bindings(map(entry.getValue())).stream().anyMatch(b -> b.host == route.port() && !b.loopback && !b.udp)
                                && referencesService(root, map(entry.getValue()), route.serviceName()));
                if (!throughRouter) errors.add(route.serviceName() + ": 주소 연결 포트 " + route.port() + "가 실제 호스트 바인딩과 일치하지 않습니다. 컨테이너 포트와 구분해주세요.");
            } else if (direct.stream().allMatch(b -> b.loopback || b.udp)) {
                errors.add(route.serviceName() + ": loopback 또는 UDP 바인딩은 현재 Tunnel 주소로 연결할 수 없습니다.");
            }
        }
        String compose = changed ? dump(root) : request.composeContent();
        validator.validate(compose).errors().forEach(error -> errors.add(error.message()));
        return new ComposePreparationResult(errors.isEmpty(), compose, uploads, checks, summaries,
                errors.stream().distinct().toList(), warnings.stream().distinct().toList());
    }

    public ComposeArtifact prepare(ComposeArtifact artifact, String context) {
        var result = inspect(new ComposePreparationRequest(artifact.composeContent(), artifact.environmentFiles(), artifact.exposedRoutes(), artifact.healthChecks(), context));
        if (!result.valid()) throw new OpsException(OpsErrorCode.INVALID_COMPOSE, String.join(" / ", result.errors()));
        return new ComposeArtifact(result.composeContent(), result.environmentFiles(), artifact.uploadedFiles(), artifact.exposedRoutes(), result.healthChecks(), artifact.sourceType());
    }

    public List<EnvironmentFile> editableEnvironmentFiles(ComposeArtifact artifact, String context) {
        Map<String, Object> root = map(new Yaml(new SafeConstructor(new LoaderOptions())).load(artifact.composeContent()));
        Map<String, Object> services = map(root.get("services"));
        String prefix = context == null || context.isBlank() || context.equals(".") ? "" : relativePath(context) + "/";
        return artifact.environmentFiles().stream().map(file -> {
            if (file.serviceNames() != null || !file.vmPath().startsWith(prefix)) return file;
            String path = file.vmPath().substring(prefix.length());
            List<String> targets = services.entrySet().stream().filter(entry -> {
                Object configured = map(entry.getValue()).get("env_file");
                List<?> values = configured instanceof List<?> list ? list : configured == null ? List.of() : List.of(configured);
                return values.stream().anyMatch(value -> path.equals(value) || value instanceof Map<?, ?> item && path.equals(item.get("path")));
            }).map(Map.Entry::getKey).toList();
            if (targets.isEmpty() && !path.equals(".env")) return file;
            return new EnvironmentFile(path, file.content(), targets);
        }).toList();
    }

    private boolean referencesService(Map<String, Object> root, Map<String, Object> service, String upstream) {
        if (!(service.get("configs") instanceof List<?> configs)) return false;
        Map<String, Object> definitions = map(root.get("configs"));
        return configs.stream().anyMatch(config -> {
            String source = config instanceof Map<?, ?> value ? String.valueOf(value.get("source")) : String.valueOf(config);
            Object content = map(definitions.get(source)).get("content");
            return content instanceof String text && text.contains("reverse_proxy " + upstream + ":");
        });
    }
    private Object resolveValues(Object value, Map<String, String> environment, List<String> errors) {
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((key, item) -> result.put(String.valueOf(key), resolveValues(item, environment, errors)));
            return result;
        }
        if (value instanceof List<?> values) return values.stream().map(item -> resolveValues(item, environment, errors)).toList();
        if (!(value instanceof String text)) return value;
        var matcher = Pattern.compile("(?<!\\$)\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?:(:?[-?+])(.*?))?}").matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1), operator = matcher.group(2), fallback = matcher.group(3);
            String replacement = environment.get(key);
            boolean missing = replacement == null || operator != null && operator.startsWith(":") && replacement.isEmpty();
            if (operator != null && operator.endsWith("+")) replacement = missing ? "" : fallback;
            else if (missing && operator != null && operator.endsWith("-")) replacement = fallback;
            else if (missing) { errors.add("Compose 치환 변수 " + key + "를 Compose 디렉터리의 .env에 입력해주세요."); replacement = ""; }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    private List<Binding> bindings(Map<String, Object> service) {
        List<Binding> result = new ArrayList<>();
        if (!(service.get("ports") instanceof List<?> ports)) return result;
        for (Object port : ports) {
            if (port instanceof Map<?, ?> spec) {
                Integer host = number(spec.get("published")), target = number(spec.get("target"));
                String ip = String.valueOf(spec.get("host_ip"));
                if (host != null && target != null) result.add(new Binding(host, target, loopback(ip), "udp".equals(spec.get("protocol"))));
            } else {
                String text = String.valueOf(port); boolean udp = text.endsWith("/udp"); text = text.replaceFirst("/(tcp|udp)$", "");
                String[] parts = text.split(":");
                if (parts.length < 2) continue;
                Integer host = number(parts[parts.length-2]), target = number(parts[parts.length-1]);
                String ip = parts.length > 2 ? String.join(":", Arrays.copyOf(parts, parts.length-2)) : "";
                if (host != null && target != null) result.add(new Binding(host, target, loopback(ip), udp));
            }
        }
        return result;
    }
    private boolean loopback(String ip) { return ip.startsWith("127.") || ip.equals("::1") || ip.equals("[::1]") || ip.equals("localhost"); }
    private Integer number(Object value) { try { int number = Integer.parseInt(String.valueOf(value)); return number > 0 && number <= 65535 ? number : null; } catch (Exception e) { return null; } }
    private String relativePath(String value) {
        if (!SAFE_PATH.matcher(value).matches() || value.startsWith("/") || Arrays.asList(value.split("/")).contains("..")) throw new IllegalArgumentException();
        return Path.of(value).normalize().toString();
    }
    private Set<String> environmentKeys(String content) {
        Set<String> result = new LinkedHashSet<>();
        for (String line : content.split("\\R")) { var matcher = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=").matcher(line); if (matcher.find()) result.add(matcher.group(1)); }
        return result;
    }
    private Set<String> environmentNames(Object environment) {
        Set<String> keys = new LinkedHashSet<>();
        if (environment instanceof Map<?, ?> map) map.keySet().forEach(key -> keys.add(String.valueOf(key)));
        if (environment instanceof List<?> list) list.forEach(value -> keys.add(String.valueOf(value).split("=", 2)[0]));
        return keys;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>(); }
    private String dump(Map<String, Object> root) { DumperOptions options = new DumperOptions(); options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); return new Yaml(options).dump(root); }
    private record Binding(int host, int target, boolean loopback, boolean udp) { }
}
