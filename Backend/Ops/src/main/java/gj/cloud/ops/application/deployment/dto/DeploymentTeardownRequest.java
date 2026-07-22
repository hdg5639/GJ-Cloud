package gj.cloud.ops.application.deployment.dto;

import java.util.List;

// removeRouteNicknames가 비어있으면 컨테이너만 내리고 노출 포트는 그대로 둔다(기본값) — 명시적으로
// 골라야만 그 포트들의 Cloudflare 라우트가 함께 정리된다.
public record DeploymentTeardownRequest(List<String> removeRouteNicknames) {
}
