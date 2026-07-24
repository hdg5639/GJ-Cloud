package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// AI-Deployment-Pipeline.md 12절 — 구조적 검증(DeploymentSpecValidator)과 보안/정책 검증을 분리.
// 참고: 이 파이프라인의 스펙 스키마는 이미 privileged/host 네트워킹/host-path 마운트/임의 리소스 값 같은
// Docker 저수준 옵션을 애초에 노출하지 않는다(DeploymentSpecRenderer가 고정된 안전한 compose 블록만 생성) —
// 그래서 그런 항목들은 "정책 위반 거부"가 아니라 애초에 스펙에 존재할 수 없는 필드다. 이 검증기는 실제로
// 이 스키마에 남아있는, 자유 문자열이라 여전히 위험할 수 있는 경로/값들(디렉토리 탈출, 시크릿 값 하드코딩)에 집중한다.
@Component
public class DeploymentSpecPolicyValidator {

    private static final Pattern SECRET_LOOKING_VALUE = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*[:=]\\s*\\S+|-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}");

    public void validate(DeploymentSpec spec) {
        List<ValidationError> errors = collectErrors(spec);
        if (!errors.isEmpty()) {
            throw new OpsException(OpsErrorCode.DEPLOYMENT_POLICY_VIOLATION);
        }
    }

    public List<ValidationError> collectErrors(DeploymentSpec spec) {
        List<ValidationError> errors = new ArrayList<>();
        for (ServiceSpec service : spec.services()) {
            checkNoPathTraversal(service.context(), "context", service.name(), errors);
            if (service.build().outputPath() != null) {
                checkNoPathTraversal(service.build().outputPath(), "build.outputPath", service.name(), errors);
            }
            if (service.artifact().path() != null) {
                checkNoPathTraversal(service.artifact().path(), "artifact.path", service.name(), errors);
            }
            checkNoSecretLookingValue(service, errors);
        }
        return errors;
    }

    private void checkNoPathTraversal(String path, String field, String serviceName, List<ValidationError> errors) {
        if (path == null) {
            return;
        }
        if (path.contains("..") || path.startsWith("/") || path.contains("~")) {
            errors.add(new ValidationError(
                    field + "가 저장소 루트를 벗어나는 경로를 참조합니다: " + path + " (service=" + serviceName + ")",
                    field + " references a path outside the repository root: " + path + " (service=" + serviceName + ")"));
        }
    }

    // 스펙의 문자열 필드에 시크릿처럼 보이는 값이 하드코딩돼 있으면 거부 — 진짜 비밀값은 배포 시점의
    // environmentFiles(암호화 저장)로만 전달돼야 하고, AI가 생성/기억한 값이 스펙 자체에 박혀서는 안 됨.
    private void checkNoSecretLookingValue(ServiceSpec service, List<ValidationError> errors) {
        String[] candidates = {service.context(), service.build().version(), service.build().outputPath(), service.artifact().path()};
        for (String candidate : candidates) {
            if (candidate != null && SECRET_LOOKING_VALUE.matcher(candidate).find()) {
                errors.add(new ValidationError(
                        "시크릿으로 보이는 값이 스펙 필드에 포함돼 있습니다 (service=" + service.name() + ")",
                        "A value that looks like a secret is embedded in a spec field (service=" + service.name() + ")"));
                break;
            }
        }
    }
}
