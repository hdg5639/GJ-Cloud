package gj.cloud.ops.application.deployment.validation;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// D.5 공통 Validator — sourceType과 무관하게 모든 배포 방식이 통과해야 하는 확정적 검사만 수행 (AI 관여 없음).
// 여기서 하는 검사는 "compose 텍스트만 보고 판단 가능한 것"으로 한정함.
// "build context 경로 존재", "docker compose config" 같은 원격 실행이 필요한 검증은 D.7 파이프라인의
// VALIDATING 단계(Executor, SSH exec)에서 실제 체크아웃 이후에 수행함 — 여기서는 할 수 없음(디렉토리가 아직 없음).
@Component
public class ComposeValidator {

    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[;&`|]|\\$\\(");
    // OPS-SEC-003: 서비스명은 이후 ComposeImageBuilder에서 `docker build -t '<tag>'` 셸 문자열에 그대로 들어가므로
    // DANGEROUS_CHARS 블랙리스트(', ", \ 누락)가 아니라 허용목록으로 검증해야 함
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,62}$");
    private static final String DOCKER_SOCK_PATH = "/var/run/docker.sock";
    private static final Set<Integer> SENSITIVE_DB_PORTS = Set.of(5432, 3306, 6379, 27017);

    public ValidationResult validate(String composeContent) {
        List<ValidationError> errors = new ArrayList<>();

        Map<String, Object> root;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(composeContent);
            if (!(loaded instanceof Map)) {
                return ValidationResult.fail(List.of(new ValidationError("compose 최상위는 YAML 맵이어야 합니다.")));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) loaded;
            root = casted;
        } catch (Exception e) {
            return ValidationResult.fail(List.of(new ValidationError("YAML 파싱 오류: " + e.getMessage())));
        }

        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map<?, ?> services) || services.isEmpty()) {
            errors.add(new ValidationError("services 정의가 없습니다."));
            return ValidationResult.fail(errors);
        }

        Set<Integer> seenHostPorts = new HashSet<>();
        for (Map.Entry<?, ?> entry : services.entrySet()) {
            String serviceName = String.valueOf(entry.getKey());
            if (!SAFE_SERVICE_NAME.matcher(serviceName).matches()) {
                errors.add(new ValidationError(
                        "서비스명은 소문자/숫자로 시작하고 소문자, 숫자, '_', '-'만 포함할 수 있습니다: " + serviceName));
            }
            if (!(entry.getValue() instanceof Map<?, ?> serviceDef)) {
                continue;
            }

            checkPortDuplication(serviceDef.get("ports"), seenHostPorts, errors);
            checkDangerousPaths(serviceDef.get("volumes"), errors);
            checkPrivileged(serviceDef.get("privileged"), serviceName, errors);
            checkHostNetwork(serviceDef.get("network_mode"), serviceName, errors);
            checkDockerSocketMount(serviceDef.get("volumes"), serviceName, errors);
            checkExposedDatabasePorts(serviceDef.get("ports"), serviceName, errors);
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private void checkPortDuplication(Object portsObj, Set<Integer> seenHostPorts, List<ValidationError> errors) {
        if (!(portsObj instanceof List<?> ports)) {
            return;
        }
        for (Object portEntry : ports) {
            Integer hostPort = extractHostPort(String.valueOf(portEntry));
            if (hostPort != null && !seenHostPorts.add(hostPort)) {
                errors.add(new ValidationError("호스트 포트가 중복됩니다: " + hostPort));
            }
        }
    }

    private void checkDangerousPaths(Object volumesObj, List<ValidationError> errors) {
        if (!(volumesObj instanceof List<?> volumes)) {
            return;
        }
        for (Object v : volumes) {
            if (v != null && DANGEROUS_CHARS.matcher(String.valueOf(v)).find()) {
                errors.add(new ValidationError("볼륨 경로에 허용되지 않는 문자가 포함되어 있습니다: " + v));
            }
        }
    }

    private void checkPrivileged(Object privilegedObj, String serviceName, List<ValidationError> errors) {
        if (Boolean.TRUE.equals(privilegedObj) || "true".equalsIgnoreCase(String.valueOf(privilegedObj))) {
            errors.add(new ValidationError(serviceName + ": privileged 모드는 허용되지 않습니다."));
        }
    }

    private void checkHostNetwork(Object networkModeObj, String serviceName, List<ValidationError> errors) {
        if (networkModeObj != null && "host".equalsIgnoreCase(String.valueOf(networkModeObj))) {
            errors.add(new ValidationError(serviceName + ": host 네트워크 모드는 허용되지 않습니다."));
        }
    }

    private void checkDockerSocketMount(Object volumesObj, String serviceName, List<ValidationError> errors) {
        if (!(volumesObj instanceof List<?> volumes)) {
            return;
        }
        for (Object v : volumes) {
            if (v != null && String.valueOf(v).contains(DOCKER_SOCK_PATH)) {
                errors.add(new ValidationError(serviceName + ": Docker 소켓 마운트는 허용되지 않습니다."));
            }
        }
    }

    // 알려진 DB 포트(5432/3306/6379/27017)가 127.0.0.1/localhost로 바인딩되지 않고 그대로 호스트에 노출되는 경우 차단
    private void checkExposedDatabasePorts(Object portsObj, String serviceName, List<ValidationError> errors) {
        if (!(portsObj instanceof List<?> ports)) {
            return;
        }
        for (Object portEntry : ports) {
            String mapping = String.valueOf(portEntry);
            Integer hostPort = extractHostPort(mapping);
            if (hostPort == null || !SENSITIVE_DB_PORTS.contains(hostPort)) {
                continue;
            }
            if (mapping.startsWith("127.0.0.1:") || mapping.startsWith("localhost:")) {
                continue;
            }
            errors.add(new ValidationError(
                    serviceName + ": DB 포트(" + hostPort + ")를 외부에 직접 노출할 수 없습니다. 127.0.0.1로 바인딩하세요."));
        }
    }

    // "8080:80", "127.0.0.1:8080:80", "8080:80/tcp" 등 지원. 컨테이너 전용 노출("80"만 있는 형태)은 호스트 포트 없음 → null
    private Integer extractHostPort(String portMapping) {
        String[] withoutProtocol = portMapping.split("/");
        String[] parts = withoutProtocol[0].split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.parseInt(parts[parts.length - 2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
