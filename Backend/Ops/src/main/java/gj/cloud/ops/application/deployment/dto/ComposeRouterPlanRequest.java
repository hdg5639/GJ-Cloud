package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.application.deployment.routing.ComposeRouterRouteOverride;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ComposeRouterPlanRequest(
        @NotBlank String composeContent,
        @Min(1) @Max(65535) Integer routerHostPort,
        Map<String, @Min(1) @Max(65535) Integer> servicePorts,
        Map<String, @Valid ComposeRouterRouteOverride> routeOverrides,
        // 배포 직전 화면에서 '공개 안 함'으로 끈 서비스 — 라우팅/CNAME에서 제외한다(내부 전용으로 남음).
        java.util.List<String> excludedServices
) {
}
