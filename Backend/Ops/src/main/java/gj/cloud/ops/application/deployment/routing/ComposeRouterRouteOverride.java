package gj.cloud.ops.application.deployment.routing;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 서비스별 라우팅 보정값.
// - PREFIX 모드(기본): 하나의 공개 도메인 아래 경로(routePath)로 라우팅한다.
// - DOMAIN 모드: 서비스 전용 서브도메인(customSubdomain)으로 노출하고 Caddy가 Host 헤더로 라우팅한다.
//   customSubdomain은 라벨만 받으며 실제 zone 결합/CNAME 등록은 VM 서비스가 담당한다.
public record ComposeRouterRouteOverride(
        @Pattern(regexp = "^(PREFIX|DOMAIN)$")
        String mode,
        @Pattern(regexp = "^$|^/(?:[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*)?$")
        String routePath,
        boolean stripPrefix,
        @Size(max = 30)
        @Pattern(regexp = "^$|^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
        String customSubdomain
) {
    public boolean isDomain() {
        return "DOMAIN".equalsIgnoreCase(mode);
    }
}
