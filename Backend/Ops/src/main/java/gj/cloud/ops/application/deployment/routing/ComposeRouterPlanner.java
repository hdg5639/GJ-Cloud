package gj.cloud.ops.application.deployment.routing;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ComposeRouterPlanner {

    public static final String ROUTER_SERVICE_NAME = "gamjabox-router";
    public static final int ROUTER_CONTAINER_PORT = 8080;
    public static final int DEFAULT_ROUTER_HOST_PORT = 18080;
    private static final String ROUTER_CONFIG_NAME = "gamjabox-caddyfile";

    private static final Set<Integer> INFRA_PORTS = Set.of(
            5432, 3306, 6379, 27017, 5672, 9092, 9200, 9300, 2181);
    private static final Pattern INFRA_NAME = Pattern.compile(
            "(?i).*(postgres|mysql|mariadb|mongo|redis|rabbit|kafka|zookeeper|elastic|opensearch|database|db).*");
    private static final Pattern ROUTER_NAME = Pattern.compile(
            "(?i)(.*[-_])?(gateway|proxy|router|ingress|edge|load-balancer)([-_].*)?");
    private static final Pattern ROUTER_IMAGE = Pattern.compile(
            "(?i).*(caddy|nginx|traefik|haproxy|envoy).*");
    private static final Pattern ROOT_SERVICE_NAME = Pattern.compile(
            "(?i)(.*[-_])?(web|frontend|front|client|ui|site|app|portal)([-_].*)?");
    private static final Pattern PRESERVE_PREFIX_NAME = Pattern.compile(
            "(?i).*(api|backend|server|gateway).*");
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile(
            "^[a-z0-9][a-z0-9_-]{0,62}$");
    private static final Pattern HEALTHCHECK_URL_PATH = Pattern.compile(
            "https?://[^/\\s\"']+(/[^\\s\"'\\],}]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_ROUTE_PATH = Pattern.compile(
            "^/(?:[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*)?$");
    private static final String RESERVED_HEALTH_PATH = "/__gamjabox_router_health";

    public ComposeRouterPlanResult plan(
            String composeContent,
            Integer requestedRouterHostPort,
            Map<String, Integer> servicePortOverrides
    ) {
        return plan(composeContent, requestedRouterHostPort, servicePortOverrides, Map.of());
    }

    public ComposeRouterPlanResult plan(
            String composeContent,
            Integer requestedRouterHostPort,
            Map<String, Integer> servicePortOverrides,
            Map<String, ComposeRouterRouteOverride> routeOverrides
    ) {
        return plan(composeContent, requestedRouterHostPort, servicePortOverrides, routeOverrides, Set.of());
    }

    public ComposeRouterPlanResult plan(
            String composeContent,
            Integer requestedRouterHostPort,
            Map<String, Integer> servicePortOverrides,
            Map<String, ComposeRouterRouteOverride> routeOverrides,
            Collection<String> excludedServices
    ) {
        Set<String> excluded = excludedServices == null ? Set.of() : new LinkedHashSet<>(excludedServices);
        Map<String, Object> root = parse(composeContent);
        Map<String, Object> services = map(root.get("services"));
        if (services == null || services.isEmpty()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        String existingRouter = findExistingRouter(services);
        if (existingRouter != null) {
            Map<String, Object> routerService = map(services.get(existingRouter));
            PortInfo portInfo = routerService == null
                    ? new PortInfo(null, null)
                    : inferPort(routerService);
            List<ComposeRouterRoute> existingRoute = portInfo.containerPort() == null
                    ? List.of()
                    : List.of(new ComposeRouterRoute(
                            existingRouter,
                            "/",
                            existingRouter + ":" + portInfo.containerPort(),
                            portInfo.containerPort(),
                            portInfo.hostPort(),
                            true,
                            false,
                            "EXISTING_ROUTER",
                            "HIGH",
                            ComposeRouterRoute.MODE_PREFIX,
                            null));
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_ALREADY_CONFIGURED,
                    composeContent, "", existingRouter, null, null,
                    existingRoute, List.of(), List.of("기존 라우터 서비스를 유지합니다."));
        }

        Map<String, Integer> overrides = servicePortOverrides == null ? Map.of() : servicePortOverrides;
        Map<String, ComposeRouterRouteOverride> safeRouteOverrides =
                routeOverrides == null ? Map.of() : routeOverrides;
        List<ServiceCandidate> candidates = new ArrayList<>();
        List<ComposeRouterUnresolvedService> unresolved = new ArrayList<>();
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            Map<String, Object> service = map(entry.getValue());
            // 사용자가 배포 직전 화면에서 '공개 안 함'으로 끈 서비스는 라우팅 후보에서 제외한다.
            // 서비스 정의는 그대로 두므로(내부 expose 유지) 컨테이너는 뜨지만 외부로는 노출되지 않는다.
            if (service == null || excluded.contains(serviceName)
                    || isInfrastructure(serviceName, service) || !isRoutingEnabled(service)) {
                continue;
            }
            if (!SAFE_SERVICE_NAME.matcher(serviceName).matches()) {
                unresolved.add(new ComposeRouterUnresolvedService(
                        serviceName,
                        "서비스명은 소문자/숫자로 시작하고 소문자, 숫자, '_' 또는 '-'만 사용할 수 있습니다.",
                        false));
                continue;
            }
            if (service.containsKey("network_mode") || service.containsKey("profiles")) {
                unresolved.add(new ComposeRouterUnresolvedService(
                        serviceName,
                        "network_mode 또는 profiles를 사용하는 서비스는 자동 라우터 네트워크에 안전하게 합칠 수 없습니다.",
                        false));
                continue;
            }
            Integer override = overrides.get(serviceName);
            PortInfo portInfo = override != null ? new PortInfo(override, null) : inferPort(service);
            if (portInfo.containerPort() == null) {
                unresolved.add(new ComposeRouterUnresolvedService(
                        serviceName,
                        "ports/expose/image에서 컨테이너 HTTP 포트를 확정할 수 없습니다.",
                        true));
                continue;
            }
            candidates.add(new ServiceCandidate(serviceName, service, portInfo));
        }

        if (!unresolved.isEmpty()) {
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_NEEDS_INPUT,
                    composeContent, "", ROUTER_SERVICE_NAME, requestedRouterHostPort, ROUTER_CONTAINER_PORT,
                    List.of(), List.copyOf(unresolved),
                    List.of("컨테이너 포트를 확정할 수 없는 서비스가 있어 Compose를 변경하지 않았습니다."));
        }
        if (candidates.size() < 2) {
            List<ComposeRouterRoute> directRoute = candidates.isEmpty()
                    ? List.of()
                    : List.of(directRoute(candidates.get(0)));
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_NOT_REQUIRED,
                    composeContent, "", candidates.isEmpty() ? ROUTER_SERVICE_NAME : candidates.get(0).name(),
                    directRoute.isEmpty() ? null : directRoute.get(0).hostPort(),
                    directRoute.isEmpty() ? null : directRoute.get(0).containerPort(),
                    directRoute, List.of(),
                    List.of("라우팅할 애플리케이션 서비스가 2개 미만이라 서비스 포트에 직접 연결합니다."));
        }

        ServiceCandidate rootService = chooseRootService(candidates, safeRouteOverrides);
        RouteBuildResult routeBuild = buildRoutes(candidates, rootService, safeRouteOverrides);
        if (!routeBuild.unresolved().isEmpty()) {
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_NEEDS_INPUT,
                    composeContent, "", ROUTER_SERVICE_NAME, requestedRouterHostPort, ROUTER_CONTAINER_PORT,
                    routeBuild.routes(), routeBuild.unresolved(), routeBuild.warnings());
        }
        List<ComposeRouterRoute> routes = routeBuild.routes();
        RouterPortSelection routerPortSelection = chooseRouterHostPort(
                services, candidates, requestedRouterHostPort);
        int routerHostPort = routerPortSelection.port();
        String caddyfile = buildCaddyfile(routes);
        String routerConfigName = availableRouterConfigName(root);

        for (ServiceCandidate candidate : candidates) {
            moveToInternalExposure(candidate.service(), candidate.portInfo().containerPort());
            services.put(candidate.name(), candidate.service());
        }
        services.put(ROUTER_SERVICE_NAME, buildRouterService(
                candidates, routerHostPort, routerConfigName));
        root.put("services", services);
        addCaddyConfig(root, routerConfigName, caddyfile);

        List<String> warnings = new ArrayList<>();
        warnings.add("기존 YAML 주석과 서식은 정규화되므로 적용 전 변경 내용을 확인하세요.");
        warnings.addAll(routeBuild.warnings());
        if (requestedRouterHostPort == null && candidates.stream().noneMatch(c -> c.portInfo().hostPort() != null)) {
            warnings.add("기존 호스트 포트가 없어 " + DEFAULT_ROUTER_HOST_PORT + "번을 기본 진입 포트로 선택했습니다.");
        }
        if (routerPortSelection.adjusted()) {
            warnings.add("요청한 진입 포트가 Compose의 다른 포트와 겹쳐 "
                    + routerHostPort + "번으로 자동 조정했습니다.");
        }

        return new ComposeRouterPlanResult(
                ComposeRouterPlanResult.STATUS_ADDED,
                dump(root),
                caddyfile,
                ROUTER_SERVICE_NAME,
                routerHostPort,
                ROUTER_CONTAINER_PORT,
                List.copyOf(routes),
                List.of(),
                List.copyOf(warnings));
    }

    private Map<String, Object> parse(String composeContent) {
        try {
            Object loaded = new Yaml().load(composeContent);
            Map<String, Object> parsed = map(loaded);
            if (parsed == null) {
                throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
            }
            return new LinkedHashMap<>(parsed);
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
    }

    private String findExistingRouter(Map<String, Object> services) {
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            Map<String, Object> service = map(entry.getValue());
            if (service == null) continue;
            String image = String.valueOf(service.getOrDefault("image", ""));
            boolean namedAsRouter = ROUTER_NAME.matcher(entry.getKey()).matches();
            boolean routerImageWithEvidence = ROUTER_IMAGE.matcher(image).matches()
                    && (hasRouterConfiguration(service) || dependencyCount(service) >= 2);
            if (namedAsRouter || routerImageWithEvidence) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean hasRouterConfiguration(Map<String, Object> service) {
        String mountedConfiguration = String.valueOf(service.getOrDefault("volumes", "")).toLowerCase(Locale.ROOT)
                + " " + String.valueOf(service.getOrDefault("configs", "")).toLowerCase(Locale.ROOT)
                + " " + String.valueOf(service.getOrDefault("command", "")).toLowerCase(Locale.ROOT);
        if (mountedConfiguration.contains("caddyfile")
                || mountedConfiguration.contains("/etc/caddy")
                || mountedConfiguration.contains("/etc/nginx")
                || mountedConfiguration.contains("nginx.conf")
                || mountedConfiguration.contains("haproxy.cfg")
                || mountedConfiguration.contains("traefik")) {
            return true;
        }

        Map<String, Object> labels = map(service.get("labels"));
        if (labels != null && labels.keySet().stream().anyMatch(this::isRouterLabel)) {
            return true;
        }
        List<?> labelList = list(service.get("labels"));
        return labelList != null && labelList.stream()
                .map(String::valueOf)
                .anyMatch(this::isRouterLabel);
    }

    private boolean isRouterLabel(String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        return normalized.startsWith("traefik.")
                || normalized.startsWith("caddy")
                || normalized.startsWith("haproxy.");
    }

    private int dependencyCount(Map<String, Object> service) {
        Map<String, Object> dependencyMap = map(service.get("depends_on"));
        if (dependencyMap != null) return dependencyMap.size();
        List<?> dependencies = list(service.get("depends_on"));
        return dependencies == null ? 0 : dependencies.size();
    }

    private boolean isInfrastructure(String serviceName, Map<String, Object> service) {
        if (INFRA_NAME.matcher(serviceName).matches()) {
            return true;
        }
        String image = String.valueOf(service.getOrDefault("image", ""));
        if (INFRA_NAME.matcher(image).matches()) {
            return true;
        }
        PortInfo port = inferPort(service);
        return port.containerPort() != null && INFRA_PORTS.contains(port.containerPort());
    }

    private boolean isRoutingEnabled(Map<String, Object> service) {
        Object labelsValue = service.get("labels");
        Map<String, Object> labelMap = map(labelsValue);
        if (labelMap != null && labelMap.containsKey("gamjabox.router.enabled")) {
            return Boolean.parseBoolean(String.valueOf(labelMap.get("gamjabox.router.enabled")));
        }
        List<?> labels = list(labelsValue);
        if (labels != null) {
            for (Object label : labels) {
                String value = String.valueOf(label);
                if (value.startsWith("gamjabox.router.enabled=")) {
                    return Boolean.parseBoolean(value.substring(value.indexOf('=') + 1));
                }
            }
        }
        return true;
    }

    private PortInfo inferPort(Map<String, Object> service) {
        List<?> ports = list(service.get("ports"));
        if (ports != null) {
            for (Object entry : ports) {
                PortInfo parsed = parsePortEntry(entry);
                if (parsed.containerPort() != null) {
                    return parsed;
                }
            }
        }
        List<?> exposed = list(service.get("expose"));
        if (exposed != null) {
            for (Object entry : exposed) {
                Integer port = integerPort(entry);
                if (port != null) {
                    return new PortInfo(port, null);
                }
            }
        }
        return new PortInfo(knownImagePort(String.valueOf(service.getOrDefault("image", ""))), null);
    }

    private PortInfo parsePortEntry(Object entry) {
        Map<String, Object> mapping = map(entry);
        if (mapping != null) {
            return new PortInfo(integerPort(mapping.get("target")), integerPort(mapping.get("published")));
        }
        String value = String.valueOf(entry).split("/")[0];
        String[] parts = value.split(":");
        Integer container = integerPort(parts[parts.length - 1]);
        Integer host = parts.length >= 2 ? integerPort(parts[parts.length - 2]) : null;
        return new PortInfo(container, host);
    }

    private Integer integerPort(Object value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim().replace("\"", ""));
            return parsed >= 1 && parsed <= 65535 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer knownImagePort(String image) {
        String normalized = image.toLowerCase(Locale.ROOT);
        if (normalized.contains("nginx") || normalized.contains("httpd") || normalized.contains("caddy")) return 80;
        if (normalized.contains("grafana")) return 3000;
        if (normalized.contains("jenkins")) return 8080;
        return null;
    }

    private ServiceCandidate chooseRootService(
            List<ServiceCandidate> candidates,
            Map<String, ComposeRouterRouteOverride> overrides
    ) {
        // 도메인 모드로 지정된 서비스는 자기 서브도메인으로 노출되므로 기본 진입점(루트)으로는 피한다.
        java.util.function.Predicate<ServiceCandidate> notDomain = candidate -> {
            ComposeRouterRouteOverride override = overrides.get(candidate.name());
            return override == null || !override.isDomain();
        };
        return candidates.stream()
                .filter(candidate -> ROOT_SERVICE_NAME.matcher(candidate.name()).matches() && notDomain.test(candidate))
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .filter(candidate -> candidate.portInfo().containerPort() == 80 && notDomain.test(candidate))
                        .findFirst()
                        .orElseGet(() -> candidates.stream()
                                .filter(notDomain)
                                .findFirst()
                                .orElse(candidates.get(0))));
    }

    private ComposeRouterRoute directRoute(ServiceCandidate candidate) {
        return new ComposeRouterRoute(
                candidate.name(),
                "/",
                upstream(candidate),
                candidate.portInfo().containerPort(),
                candidate.portInfo().hostPort(),
                true,
                false,
                "DIRECT",
                "HIGH",
                ComposeRouterRoute.MODE_PREFIX,
                null);
    }

    private RouteBuildResult buildRoutes(
            List<ServiceCandidate> candidates,
            ServiceCandidate rootService,
            Map<String, ComposeRouterRouteOverride> overrides
    ) {
        List<ComposeRouterRoute> routes = new ArrayList<>();
        List<ComposeRouterUnresolvedService> unresolved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> usedRouteSlugs = new LinkedHashSet<>();

        ComposeRouterRouteOverride rootOverride = overrides.get(rootService.name());
        if (rootOverride != null && rootOverride.isDomain()) {
            unresolved.add(new ComposeRouterUnresolvedService(
                    rootService.name(),
                    "루트 서비스는 기본 진입점이라 도메인 모드로 분리할 수 없습니다.",
                    false));
        } else if (rootOverride != null
                && rootOverride.routePath() != null
                && !rootOverride.routePath().isBlank()
                && !"/".equals(normalizeRoutePath(rootOverride.routePath()))) {
            unresolved.add(new ComposeRouterUnresolvedService(
                    rootService.name(),
                    "루트 서비스는 공개 Prefix '/'를 유지해야 합니다.",
                    false));
        }
        routes.add(new ComposeRouterRoute(
                rootService.name(),
                "/",
                upstream(rootService),
                rootService.portInfo().containerPort(),
                rootService.portInfo().hostPort(),
                true,
                false,
                rootOverride == null ? "ROOT_DEFAULT" : "USER",
                "HIGH",
                ComposeRouterRoute.MODE_PREFIX,
                null));
        candidates.stream()
                .filter(candidate -> candidate != rootService)
                .sorted(Comparator.comparing(ServiceCandidate::name))
                .forEach(candidate -> {
                    ComposeRouterRouteOverride override = overrides.get(candidate.name());
                    // DOMAIN 모드 — 서비스 전용 서브도메인으로 노출한다(호스트 기반 라우팅).
                    if (override != null && override.isDomain()) {
                        String subdomain = override.customSubdomain();
                        if (subdomain == null || subdomain.isBlank()) {
                            unresolved.add(new ComposeRouterUnresolvedService(
                                    candidate.name(),
                                    "도메인 모드는 서비스 전용 서브도메인이 필요합니다.",
                                    false));
                            return;
                        }
                        routes.add(new ComposeRouterRoute(
                                candidate.name(),
                                null,
                                upstream(candidate),
                                candidate.portInfo().containerPort(),
                                candidate.portInfo().hostPort(),
                                false,
                                false,
                                "USER",
                                "HIGH",
                                ComposeRouterRoute.MODE_DOMAIN,
                                subdomain));
                        return;
                    }
                    // PREFIX 모드 — 하나의 공개 도메인 아래 경로로 라우팅한다.
                    String routePath;
                    boolean stripPrefix;
                    String source;
                    String confidence;
                    if (override != null) {
                        routePath = normalizeRoutePath(override.routePath());
                        stripPrefix = override.stripPrefix();
                        source = "USER";
                        confidence = "HIGH";
                        if ("/".equals(routePath)) {
                            unresolved.add(new ComposeRouterUnresolvedService(
                                    candidate.name(),
                                    "루트 Prefix '/'는 하나만 사용할 수 있습니다.",
                                    false));
                        } else if (RESERVED_HEALTH_PATH.equals(routePath)
                                || routePath.startsWith(RESERVED_HEALTH_PATH + "/")) {
                            unresolved.add(new ComposeRouterUnresolvedService(
                                    candidate.name(),
                                    RESERVED_HEALTH_PATH + " 경로는 라우터 헬스체크용으로 예약되어 있습니다.",
                                    false));
                        }
                    } else {
                        String inferredPath = inferRoutePath(candidate.service());
                        String baseSlug = inferredPath != null
                                ? inferredPath.substring(1)
                                : routeSlug(candidate.name());
                        String uniqueSlug = baseSlug;
                        int suffix = 2;
                        while (!usedRouteSlugs.add(uniqueSlug)) {
                            uniqueSlug = baseSlug + "-" + suffix;
                            suffix += 1;
                        }
                        routePath = "/" + uniqueSlug;
                        stripPrefix = inferredPath == null
                                && !PRESERVE_PREFIX_NAME.matcher(candidate.name()).matches();
                        source = inferredPath == null ? "SERVICE_NAME" : "HEALTHCHECK";
                        confidence = inferredPath == null ? "LOW" : "HIGH";
                    }
                    routes.add(new ComposeRouterRoute(
                            candidate.name(),
                            routePath,
                            upstream(candidate),
                            candidate.portInfo().containerPort(),
                            candidate.portInfo().hostPort(),
                            false,
                            stripPrefix,
                            source,
                            confidence,
                            ComposeRouterRoute.MODE_PREFIX,
                            null));
                });

        // PREFIX 라우트는 경로 중복을, DOMAIN 라우트는 서브도메인 중복을 각각 검사한다.
        Map<String, List<String>> servicesByPath = new LinkedHashMap<>();
        routes.stream()
                .filter(route -> !route.isDomain())
                .forEach(route -> servicesByPath
                        .computeIfAbsent(route.routePath(), ignored -> new ArrayList<>())
                        .add(route.serviceName()));
        servicesByPath.forEach((path, services) -> {
            if (services.size() > 1) {
                services.forEach(service -> unresolved.add(new ComposeRouterUnresolvedService(
                        service,
                        "다른 서비스와 공개 Prefix '" + path + "'가 중복됩니다.",
                        false)));
            }
        });
        Map<String, List<String>> servicesBySubdomain = new LinkedHashMap<>();
        routes.stream()
                .filter(ComposeRouterRoute::isDomain)
                .forEach(route -> servicesBySubdomain
                        .computeIfAbsent(route.customSubdomain().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(route.serviceName()));
        servicesBySubdomain.forEach((subdomain, services) -> {
            if (services.size() > 1) {
                services.forEach(service -> unresolved.add(new ComposeRouterUnresolvedService(
                        service,
                        "다른 서비스와 서브도메인 '" + subdomain + "'가 중복됩니다.",
                        false)));
            }
        });

        List<ComposeRouterRoute> nonRootPrefix = routes.stream()
                .filter(route -> !route.root() && !route.isDomain())
                .toList();
        for (int left = 0; left < nonRootPrefix.size(); left += 1) {
            for (int right = left + 1; right < nonRootPrefix.size(); right += 1) {
                String leftPath = nonRootPrefix.get(left).routePath();
                String rightPath = nonRootPrefix.get(right).routePath();
                if (leftPath.startsWith(rightPath + "/") || rightPath.startsWith(leftPath + "/")) {
                    warnings.add("Prefix " + leftPath + "와 " + rightPath
                            + "가 중첩되어 더 구체적인 경로를 먼저 매칭합니다.");
                }
            }
        }
        routes.stream()
                .filter(route -> !route.isDomain() && "LOW".equals(route.confidence()))
                .forEach(route -> warnings.add(route.serviceName()
                        + "의 Prefix " + route.routePath()
                        + "는 서비스명으로 추정했습니다. 분석 결과에서 확인하거나 수정하세요."));
        return new RouteBuildResult(
                List.copyOf(routes),
                List.copyOf(unresolved),
                warnings.stream().distinct().toList());
    }

    private String normalizeRoutePath(String value) {
        if (value == null || value.isBlank()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        String normalized = value.trim().replaceAll("/+$", "");
        if (normalized.isBlank()) normalized = "/";
        if (!SAFE_ROUTE_PATH.matcher(normalized).matches()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        return normalized;
    }

    private String inferRoutePath(Map<String, Object> service) {
        Object healthcheck = service.get("healthcheck");
        if (healthcheck == null) return null;
        Matcher matcher = HEALTHCHECK_URL_PATH.matcher(String.valueOf(healthcheck));
        if (!matcher.find()) return null;
        String path = matcher.group(1).split("[?#]", 2)[0];
        path = path.replaceFirst(
                "(?i)/(?:actuator/)?(?:health|healthz|ready|readiness|live|liveness)/?$",
                "");
        path = path.replaceAll("/+$", "");
        if (path.isBlank() || !SAFE_ROUTE_PATH.matcher(path).matches()) return null;
        return path;
    }

    private String buildCaddyfile(List<ComposeRouterRoute> routes) {
        StringBuilder config = new StringBuilder();
        config.append(":").append(ROUTER_CONTAINER_PORT).append(" {\n")
                .append("  encode zstd gzip\n")
                .append("  log {\n")
                .append("    output stdout\n")
                .append("    format console\n")
                .append("  }\n\n")
                .append("  @gamjabox_health path /__gamjabox_router_health\n")
                .append("  respond @gamjabox_health 200\n\n");

        int matcherIndex = 0;
        // 도메인(호스트 기반) 라우트를 먼저 매칭 — cloudflared가 전달한 Host 헤더의 첫 라벨로 구분한다.
        // Ops는 zone을 모르므로 '^<라벨>\.' 정규식으로 라벨 경계까지만 매칭한다(실제 zone 결합은 VM 서비스).
        List<ComposeRouterRoute> domainRoutes = routes.stream()
                .filter(ComposeRouterRoute::isDomain)
                .toList();
        for (ComposeRouterRoute route : domainRoutes) {
            String matcher = "host_"
                    + route.customSubdomain().replaceAll("[^A-Za-z0-9_]", "_")
                    + "_" + matcherIndex++;
            config.append("  @").append(matcher)
                    .append(" host_regexp ^").append(route.customSubdomain()).append("\\.\n")
                    .append("  handle @").append(matcher).append(" {\n")
                    .append("    reverse_proxy ").append(route.upstream()).append("\n")
                    .append("  }\n\n");
        }
        List<ComposeRouterRoute> orderedRoutes = routes.stream()
                .filter(route -> !route.root() && !route.isDomain())
                .sorted(Comparator.comparingInt((ComposeRouterRoute route) -> route.routePath().length()).reversed())
                .toList();
        for (ComposeRouterRoute route : orderedRoutes) {
            String matcher = "route_"
                    + route.routePath().substring(1).replaceAll("[^A-Za-z0-9_]", "_")
                    + "_" + matcherIndex++;
            config.append("  @").append(matcher)
                    .append(" path ").append(route.routePath()).append(" ").append(route.routePath()).append("/*\n")
                    .append("  handle @").append(matcher).append(" {\n");
            if (route.stripPrefix()) {
                config.append("    uri strip_prefix ").append(route.routePath()).append("\n");
            }
            config.append("    reverse_proxy ").append(route.upstream()).append("\n")
                    .append("  }\n\n");
        }

        ComposeRouterRoute root = routes.stream().filter(ComposeRouterRoute::root).findFirst().orElseThrow();
        config.append("  handle {\n")
                .append("    reverse_proxy ").append(root.upstream()).append("\n")
                .append("  }\n")
                .append("}\n");
        return config.toString();
    }

    private Map<String, Object> buildRouterService(
            List<ServiceCandidate> candidates,
            int routerHostPort,
            String routerConfigName
    ) {
        Map<String, Object> router = new LinkedHashMap<>();
        router.put("image", "caddy:2.10-alpine");
        router.put("restart", "unless-stopped");
        // cloudflared는 VM의 내부 IP:port로 접근하므로 loopback에만 바인딩하면 터널에서 도달할 수 없다.
        router.put("ports", List.of(routerHostPort + ":" + ROUTER_CONTAINER_PORT));
        router.put("configs", List.of(Map.of(
                "source", routerConfigName,
                "target", "/etc/caddy/Caddyfile")));
        router.put("depends_on", candidates.stream().map(ServiceCandidate::name).toList());
        router.put("healthcheck", Map.of(
                "test", List.of(
                        "CMD", "wget", "--quiet", "--tries=1", "--spider",
                        "http://127.0.0.1:" + ROUTER_CONTAINER_PORT + "/__gamjabox_router_health"),
                "interval", "10s",
                "timeout", "3s",
                "retries", 5));

        Set<String> networks = new LinkedHashSet<>();
        for (ServiceCandidate candidate : candidates) {
            Object serviceNetworksValue = candidate.service().get("networks");
            Map<String, Object> serviceNetworkMap = map(serviceNetworksValue);
            List<?> serviceNetworks = list(serviceNetworksValue);
            if (serviceNetworkMap != null) {
                networks.addAll(serviceNetworkMap.keySet());
            } else if (serviceNetworks == null || serviceNetworks.isEmpty()) {
                networks.add("default");
            } else {
                for (Object network : serviceNetworks) {
                    networks.add(String.valueOf(network));
                }
            }
        }
        if (!networks.isEmpty()) {
            router.put("networks", List.copyOf(networks));
        }
        return router;
    }

    private String availableRouterConfigName(Map<String, Object> root) {
        Map<String, Object> configs = map(root.get("configs"));
        if (configs == null || !configs.containsKey(ROUTER_CONFIG_NAME)) {
            return ROUTER_CONFIG_NAME;
        }
        int suffix = 2;
        while (configs.containsKey(ROUTER_CONFIG_NAME + "-" + suffix)) {
            suffix += 1;
        }
        return ROUTER_CONFIG_NAME + "-" + suffix;
    }

    private void addCaddyConfig(Map<String, Object> root, String routerConfigName, String caddyfile) {
        Map<String, Object> configs = map(root.get("configs"));
        Map<String, Object> mutableConfigs = configs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(configs);
        mutableConfigs.put(routerConfigName, Map.of("content", caddyfile));
        root.put("configs", mutableConfigs);
    }

    private void moveToInternalExposure(Map<String, Object> service, int containerPort) {
        List<?> ports = list(service.get("ports"));
        if (ports != null) {
            List<Object> remainingPorts = new ArrayList<>();
            boolean removedRoutedPort = false;
            for (Object portEntry : ports) {
                PortInfo parsed = parsePortEntry(portEntry);
                if (!removedRoutedPort && Integer.valueOf(containerPort).equals(parsed.containerPort())) {
                    removedRoutedPort = true;
                } else {
                    remainingPorts.add(portEntry);
                }
            }
            if (remainingPorts.isEmpty()) {
                service.remove("ports");
            } else {
                service.put("ports", remainingPorts);
            }
        }
        Set<Integer> exposedPorts = new LinkedHashSet<>();
        List<?> existing = list(service.get("expose"));
        if (existing != null) {
            for (Object value : existing) {
                Integer parsed = integerPort(value);
                if (parsed != null) exposedPorts.add(parsed);
            }
        }
        exposedPorts.add(containerPort);
        service.put("expose", List.copyOf(exposedPorts));
    }

    private RouterPortSelection chooseRouterHostPort(
            Map<String, Object> services,
            List<ServiceCandidate> candidates,
            Integer requested
    ) {
        Set<Integer> retainedHostPorts = new LinkedHashSet<>();
        for (Object serviceValue : services.values()) {
            Map<String, Object> service = map(serviceValue);
            if (service == null) continue;
            List<?> ports = list(service.get("ports"));
            if (ports == null) continue;
            for (Object port : ports) {
                Integer hostPort = parsePortEntry(port).hostPort();
                if (hostPort != null) retainedHostPorts.add(hostPort);
            }
        }
        // 후보 서비스의 기본 HTTP 매핑은 아래에서 제거되므로 Caddy 진입 포트로 재사용할 수 있다.
        candidates.stream()
                .map(candidate -> candidate.portInfo().hostPort())
                .filter(port -> port != null)
                .forEach(retainedHostPorts::remove);

        int preferred = requested != null
                ? requested
                : candidates.stream()
                .map(candidate -> candidate.portInfo().hostPort())
                .filter(port -> port != null)
                .findFirst()
                .orElse(DEFAULT_ROUTER_HOST_PORT);
        int selected = nextFreePort(preferred, retainedHostPorts);
        return new RouterPortSelection(selected, selected != preferred);
    }

    private int nextFreePort(int preferred, Set<Integer> occupied) {
        for (int port = preferred; port <= 65535; port += 1) {
            if (!occupied.contains(port)) return port;
        }
        for (int port = 1024; port < preferred; port += 1) {
            if (!occupied.contains(port)) return port;
        }
        throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
    }

    private String upstream(ServiceCandidate candidate) {
        return candidate.name() + ":" + candidate.portInfo().containerPort();
    }

    private String routeSlug(String serviceName) {
        String slug = serviceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "service" : slug;
    }

    private String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options).dump(root);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<?> list(Object value) {
        if (value instanceof Collection<?> collection) return List.copyOf(collection);
        return null;
    }

    private record PortInfo(Integer containerPort, Integer hostPort) {
    }

    private record ServiceCandidate(String name, Map<String, Object> service, PortInfo portInfo) {
    }

    private record RouteBuildResult(
            List<ComposeRouterRoute> routes,
            List<ComposeRouterUnresolvedService> unresolved,
            List<String> warnings
    ) {
    }

    private record RouterPortSelection(int port, boolean adjusted) {
    }
}
