package gj.cloud.ops.application.preview.planning.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Workflow Composition Phase 2 §5의 실행 가능한 페이지 정본 모델. PageDraft는 구버전 요청과
// FALLBACK_CRUD 호환을 위해 남겨두지만, 계획 Patch·Blueprint 컴파일·배포 Snapshot·Runtime은
// PagePlan의 route/layout/navigation/features를 직접 소비한다.
public record PagePlan(
        String id,
        String title,
        String route,
        PageType pageType,
        // LayoutBlueprint registry key. 최종 검증에서 pageType 호환성을 확인한다.
        String layoutRef,
        List<String> capabilityIds,
        List<RouteParameter> routeParameters,
        List<String> queryParameters,
        List<NavigationRule> navigationRules,
        // 예: {"quickActions": true, "statusSummary": false, "childResourceTabs": false}.
        Map<String, Boolean> features,
        // RepositoryEvidence.CONFIDENCE_HIGH 등 Capability confidence와 같은 상수 재사용.
        String confidence,
        String reason,
        List<String> unsupportedCapabilityWarnings
) {
    public PagePlan {
        capabilityIds = immutableList(capabilityIds);
        routeParameters = immutableList(routeParameters);
        queryParameters = immutableList(queryParameters);
        navigationRules = immutableList(navigationRules);
        features = immutableMap(features);
        unsupportedCapabilityWarnings = immutableList(unsupportedCapabilityWarnings);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
