package gj.cloud.ops.application.preview.planning.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Workflow Composition Phase 2 §7의 명시적 Navigation 규칙. Portal과 배포 Runtime이 같은
// 규칙을 소비하며, row.select/create.success 등의 trigger에서 제한된 FlowExpression으로 route 값을 만든다.
public record NavigationRule(
        String sourcePageId,
        // 열린 문자열 — 문서 예시가 "row.select" 하나뿐이라 지금은 트리거 종류를 못박지 않는다.
        String trigger,
        NavigationType type,
        String targetPageId,
        // 예: {"vmId": "$row.id"} — 실제 표현식 해석은 §6/§8(API Result Chaining)의 몫.
        Map<String, String> parameters
) {
    public NavigationRule {
        parameters = parameters == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    // §7 "Required navigation types" 그대로.
    public enum NavigationType {
        OPEN_PAGE,
        OPEN_OVERLAY,
        GO_BACK,
        REPLACE_ROUTE
    }
}
