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
        String customSubdomain,
        // 다중 서비스 Caddy 라우팅 보정값. null이면 저장소 근거로 자동 추론한다.
        @Pattern(regexp = "^$|^/(?:[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*)?$")
        String routePath,
        Boolean stripPrefix,
        // 통합 Caddy 라우팅에서 이 서비스의 노출 방식. "PREFIX"(기본, 경로 기반) | "DOMAIN"(호스트 기반, customSubdomain 사용).
        // null이면 PREFIX로 취급한다.
        @Pattern(regexp = "^$|^(PREFIX|DOMAIN)$")
        String routeMode
) {
    public boolean isDomainRouting() {
        return "DOMAIN".equalsIgnoreCase(routeMode);
    }
}
