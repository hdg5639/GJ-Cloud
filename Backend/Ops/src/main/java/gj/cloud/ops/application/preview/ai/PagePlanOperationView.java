package gj.cloud.ops.application.preview.ai;

import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PageType;

import java.util.List;

// /plan/propose 응답용 검토 View. 원본 operation 필드에 순서 기반 id와 개별 구조 검증 결과를 더한다.
// valid=true는 "이 시점의 누적 제안 상태에 구조적으로 적용 가능"이라는 뜻이며, 사용자가 선택한
// 조합 전체가 배포 가능한지는 /plan/apply의 최종 검증이 다시 판단한다.
public record PagePlanOperationView(
        String id,
        PagePlanOperationType type,
        String pageId,
        String otherPageId,
        String newTitle,
        String capabilityId,
        String destinationPageId,
        List<String> capabilityIds,
        PageType pageType,
        String layoutRef,
        String featureKey,
        Boolean featureEnabled,
        NavigationRule navigationRule,
        FlowBlueprint flow,
        String flowId,
        String actionId,
        String reason,
        boolean valid,
        String validationError
) {
    public static PagePlanOperationView from(String id, PagePlanOperation operation, boolean valid,
                                              String validationError) {
        return new PagePlanOperationView(
                id,
                operation.type(),
                operation.pageId(),
                operation.otherPageId(),
                operation.newTitle(),
                operation.capabilityId(),
                operation.destinationPageId(),
                operation.capabilityIds(),
                operation.pageType(),
                operation.layoutRef(),
                operation.featureKey(),
                operation.featureEnabled(),
                operation.navigationRule(),
                operation.flow(),
                operation.flowId(),
                operation.actionId(),
                operation.reason(),
                valid,
                validationError
        );
    }
}
