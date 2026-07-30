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

    public ComposeRouterPlanResult plan(
            String composeContent,
            Integer requestedRouterHostPort,
            Map<String, Integer> servicePortOverrides
    ) {
        Map<String, Object> root = parse(composeContent);
        Map<String, Object> services = map(root.get("services"));
        if (services == null || services.isEmpty()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }

        String existingRouter = findExistingRouter(services);
        if (existingRouter != null) {
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_ALREADY_CONFIGURED,
                    composeContent, "", existingRouter, null, null,
                    List.of(), List.of(), List.of("기존 라우터 서비스를 유지합니다."));
        }

        Map<String, Integer> overrides = servicePortOverrides == null ? Map.of() : servicePortOverrides;
        List<ServiceCandidate> candidates = new ArrayList<>();
        List<ComposeRouterUnresolvedService> unresolved = new ArrayList<>();
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String serviceName = entry.getKey();
            Map<String, Object> service = map(entry.getValue());
            if (service == null || isInfrastructure(serviceName, service) || !isRoutingEnabled(service)) {
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

        if (candidates.size() + unresolved.size() < 2) {
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_NOT_REQUIRED,
                    composeContent, "", ROUTER_SERVICE_NAME, null, null,
                    List.of(), List.of(), List.of("라우팅할 애플리케이션 서비스가 2개 미만입니다."));
        }
        if (!unresolved.isEmpty()) {
            return new ComposeRouterPlanResult(
                    ComposeRouterPlanResult.STATUS_NEEDS_INPUT,
                    composeContent, "", ROUTER_SERVICE_NAME, requestedRouterHostPort, ROUTER_CONTAINER_PORT,
                    List.of(), List.copyOf(unresolved),
                    List.of("컨테이너 포트를 확정할 수 없는 서비스가 있어 Compose를 변경하지 않았습니다."));
        }

        ServiceCandidate rootService = chooseRootService(candidates);
        List<ComposeRouterRoute> routes = buildRoutes(candidates, rootService);
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

    private ServiceCandidate chooseRootService(List<ServiceCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> ROOT_SERVICE_NAME.matcher(candidate.name()).matches())
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .filter(candidate -> candidate.portInfo().containerPort() == 80)
                        .findFirst()
                        .orElse(candidates.get(0)));
    }

    private List<ComposeRouterRoute> buildRoutes(
            List<ServiceCandidate> candidates,
            ServiceCandidate rootService
    ) {
        List<ComposeRouterRoute> routes = new ArrayList<>();
        Set<String> usedRouteSlugs = new LinkedHashSet<>();
        routes.add(new ComposeRouterRoute(
                rootService.name(), "/", upstream(rootService), rootService.portInfo().containerPort(), true));
        candidates.stream()
                .filter(candidate -> candidate != rootService)
                .sorted(Comparator.comparing(ServiceCandidate::name))
                .forEach(candidate -> {
                    String baseSlug = routeSlug(candidate.name());
                    String uniqueSlug = baseSlug;
                    int suffix = 2;
                    while (!usedRouteSlugs.add(uniqueSlug)) {
                        uniqueSlug = baseSlug + "-" + suffix;
                        suffix += 1;
                    }
                    routes.add(new ComposeRouterRoute(
                            candidate.name(),
                            "/" + uniqueSlug,
                            upstream(candidate),
                            candidate.portInfo().containerPort(),
                            false));
                });
        return routes;
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

        for (ComposeRouterRoute route : routes) {
            if (route.root()) continue;
            String matcher = "route_"
                    + route.routePath().substring(1).replace("-", "_");
            config.append("  @").append(matcher)
                    .append(" path ").append(route.routePath()).append(" ").append(route.routePath()).append("/*\n")
                    .append("  handle @").append(matcher).append(" {\n");
            if (!PRESERVE_PREFIX_NAME.matcher(route.serviceName()).matches()) {
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

    private record RouterPortSelection(int port, boolean adjusted) {
    }
}
