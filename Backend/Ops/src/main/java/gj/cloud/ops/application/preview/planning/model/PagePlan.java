package gj.cloud.ops.application.preview.planning.model;

import java.util.List;
import java.util.Map;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §5 — PageDraft(id/title/
// skeleton/capabilityIds 4필드)를 대체할 풍부한 페이지 모델. §22 우선순위 1번(WP-1)으로, 지금은
// PagePlanMapper가 기존 PageDraft 생성기 결과에서 결정론적으로 파생만 한다 — BlueprintCompiler/
// PagePlanValidator/AiPagePlanner/배포 경로는 여전히 PageDraft만 쓴다(PageDraft는 폴백으로 유지,
// WP-4/5/6에서 이 모델을 실제로 소비하게 만들 예정).
public record PagePlan(
        String id,
        String title,
        String route,
        PageType pageType,
        // LayoutBlueprint(§10)가 아직 없어 항상 null — 그 작업(§22 8번)에서 채운다.
        String layoutRef,
        List<String> capabilityIds,
        // Navigation(§7, §22 4번)에서 채운다 — 지금은 항상 빈 리스트.
        List<RouteParameter> routeParameters,
        // 마찬가지로 지금은 항상 빈 리스트.
        List<String> queryParameters,
        // 마찬가지로 지금은 항상 빈 리스트.
        List<NavigationRule> navigationRules,
        // 예: {"quickActions": true, "statusSummary": false, "childResourceTabs": false}.
        Map<String, Boolean> features,
        // RepositoryEvidence.CONFIDENCE_HIGH 등 Capability confidence와 같은 상수 재사용.
        String confidence,
        String reason,
        // 아직 그런 감지 로직이 없어 항상 빈 리스트.
        List<String> unsupportedCapabilityWarnings
) {
}
