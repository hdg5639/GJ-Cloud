package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// enabled로 외부(Cloudflare 도메인) 노출 대상을 명시적으로 구분 (D-3 예시). DB/Redis 등은 기본값 false로 둬야 함.
public record ExposeSpec(
        boolean enabled,
        String protocol,
        String healthCheckPath,
        // PRO 전용. null이면 VM 기본 서브도메인+서비스명 조합을 사용한다.
        @Size(max = 30)
        @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String customSubdomain
) {
}
