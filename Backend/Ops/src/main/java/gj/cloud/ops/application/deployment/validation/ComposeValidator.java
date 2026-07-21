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
    private static final Set<Integer> SENSITIVE_DB_PORTS = Set.of(5432, 3306, 6379, 27017);
    // DEP-003: DEPLOY 권한 보유자(Owner/Admin)는 이미 자기 VM에 SSH 풀 액세스가 있어 이 필드들을 막아도
    // 셸로 우회 가능함 — 여기 목적은 "악의적 사용자 차단"이 아니라 AI 생성/오타로 위험한 옵션이 실수로
    // compose에 들어가는 것을 막는 것(실수 방지). 그래도 실제로 컨테이너 격리를 무력화하는 필드들이라 값을 본다.
    private static final Set<String> SENSITIVE_BIND_MOUNT_SOURCES = Set.of(
            "/", "/etc", "/root", "/home", "/boot", "/sys", "/proc", "/var/run/docker.sock");

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
            checkSensitiveBindMounts(serviceDef.get("volumes"), serviceName, errors);
            checkExposedDatabasePorts(serviceDef.get("ports"), serviceName, errors);
            checkPidMode(serviceDef.get("pid"), serviceName, errors);
            checkIpcMode(serviceDef.get("ipc"), serviceName, errors);
            checkDevices(serviceDef.get("devices"), serviceName, errors);
            checkCapAdd(serviceDef.get("cap_add"), serviceName, errors);
            checkSecurityOpt(serviceDef.get("security_opt"), serviceName, errors);
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

    // 바인드 마운트 소스가 민감 경로(호스트 전체를 사실상 노출하는 수준)인 경우 거부.
    // "소스:대상[:옵션]" 형태의 문자열 volumes만 대상 — named volume(콜론 없이 볼륨명만 있는 형태)은 해당 없음.
    private void checkSensitiveBindMounts(Object volumesObj, String serviceName, List<ValidationError> errors) {
        if (!(volumesObj instanceof List<?> volumes)) {
            return;
        }
        for (Object v : volumes) {
            if (v == null) continue;
            String[] parts = String.valueOf(v).split(":");
            if (parts.length < 2) continue;
            String source = parts[0];
            if (SENSITIVE_BIND_MOUNT_SOURCES.contains(source)) {
                errors.add(new ValidationError(serviceName + ": 민감한 호스트 경로 바인드 마운트는 허용되지 않습니다: " + source));
            }
        }
    }

    private void checkPidMode(Object pidObj, String serviceName, List<ValidationError> errors) {
        if (pidObj != null && "host".equalsIgnoreCase(String.valueOf(pidObj))) {
            errors.add(new ValidationError(serviceName + ": pid: host는 허용되지 않습니다."));
        }
    }

    private void checkIpcMode(Object ipcObj, String serviceName, List<ValidationError> errors) {
        if (ipcObj != null && "host".equalsIgnoreCase(String.valueOf(ipcObj))) {
            errors.add(new ValidationError(serviceName + ": ipc: host는 허용되지 않습니다."));
        }
    }

    private void checkDevices(Object devicesObj, String serviceName, List<ValidationError> errors) {
        if (devicesObj instanceof List<?> devices && !devices.isEmpty()) {
            errors.add(new ValidationError(serviceName + ": devices(호스트 디바이스 전달)는 허용되지 않습니다."));
        }
    }

    private void checkCapAdd(Object capAddObj, String serviceName, List<ValidationError> errors) {
        if (capAddObj instanceof List<?> caps && !caps.isEmpty()) {
            errors.add(new ValidationError(serviceName + ": cap_add는 허용되지 않습니다."));
        }
    }

    private void checkSecurityOpt(Object securityOptObj, String serviceName, List<ValidationError> errors) {
        if (!(securityOptObj instanceof List<?> opts)) {
            return;
        }
        for (Object opt : opts) {
            String value = String.valueOf(opt).toLowerCase();
            if (value.contains("seccomp:unconfined") || value.contains("apparmor:unconfined") || value.contains("apparmor=unconfined")) {
                errors.add(new ValidationError(serviceName + ": seccomp/apparmor 프로필 해제는 허용되지 않습니다: " + opt));
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
