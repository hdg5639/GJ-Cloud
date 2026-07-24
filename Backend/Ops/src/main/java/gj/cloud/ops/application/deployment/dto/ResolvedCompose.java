package gj.cloud.ops.application.deployment.dto;

import java.util.Map;

// build 지시문이 제거되고 이미지 태그가 고정된 버전 — 재기동/롤백은 항상 이것만 사용 (재빌드 없음, D.7)
public record ResolvedCompose(String resolvedComposeContent, Map<String, ServiceImageRef> serviceImageRefs) {
}
