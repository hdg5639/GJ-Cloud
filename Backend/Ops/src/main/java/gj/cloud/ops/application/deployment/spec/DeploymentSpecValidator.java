package gj.cloud.ops.application.deployment.spec;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

// gamjabox가 스펙 검증(schemaVersion 기준) 후 compose 렌더링 — D-3절 "생성 후" 단계 그대로 D-1에도 적용.
@Component
public class DeploymentSpecValidator {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0");
    private static final Set<String> SUPPORTED_RUNTIMES =
            Set.of("spring-boot", "nextjs", "react-nginx", "nodejs", "nestjs", "python");
    private static final Set<String> SUPPORTED_INFRA_TYPES = Set.of("postgresql", "mysql", "redis", "mongodb");

    public void validate(DeploymentSpec spec) {
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(spec.schemaVersion())) {
            throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
        }
        for (ServiceSpec service : spec.services()) {
            if (!SUPPORTED_RUNTIMES.contains(service.runtime())) {
                throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
            }
        }
        if (spec.infrastructure() != null) {
            for (InfrastructureSpec infra : spec.infrastructure()) {
                if (!SUPPORTED_INFRA_TYPES.contains(infra.type())) {
                    throw new OpsException(OpsErrorCode.INVALID_COMPOSE);
                }
            }
        }
    }
}
