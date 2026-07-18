package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// gamjabox가 스펙 검증(schemaVersion 기준) 후 compose 렌더링 — D-3절 "생성 후" 단계 그대로 D-1에도 적용.
@Component
public class DeploymentSpecValidator {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0");
    private static final Set<String> SUPPORTED_RUNTIMES =
            Set.of("spring-boot", "nextjs", "react-nginx", "nodejs", "nestjs", "python");
    private static final Set<String> SUPPORTED_INFRA_TYPES = Set.of("postgresql", "mysql", "redis", "mongodb");

    public void validate(DeploymentSpec spec) {
        if (!collectErrors(spec).isEmpty()) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
    }

    // D-3 AI 자동 재교정 — 실패 사유를 그대로 AI에게 돌려줘야 하므로 예외 대신 메시지 목록으로 반환
    public List<String> collectErrors(DeploymentSpec spec) {
        List<String> errors = new ArrayList<>();
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(spec.schemaVersion())) {
            errors.add("지원하지 않는 schemaVersion입니다: " + spec.schemaVersion());
        }
        for (ServiceSpec service : spec.services()) {
            if (!SUPPORTED_RUNTIMES.contains(service.runtime())) {
                errors.add("지원하지 않는 runtime입니다: " + service.runtime() + " (service=" + service.name() + ")");
            }
        }
        if (spec.infrastructure() != null) {
            for (InfrastructureSpec infra : spec.infrastructure()) {
                if (!SUPPORTED_INFRA_TYPES.contains(infra.type())) {
                    errors.add("지원하지 않는 infrastructure type입니다: " + infra.type());
                }
            }
        }
        return errors;
    }
}
