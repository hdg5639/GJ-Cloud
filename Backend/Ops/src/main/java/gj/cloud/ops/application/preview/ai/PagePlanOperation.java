package gj.cloud.ops.application.preview.ai;

import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PageType;

import java.util.List;

// operation 종류마다 실제로 쓰는 필드가 다르며, 나머지는 null이다.
//
// RENAME_PAGE      pageId, newTitle
// MERGE_PAGES      pageId(유지), otherPageId(제거)
// MOVE_CAPABILITY  capabilityId, destinationPageId
// ADD_PAGE         pageId, newTitle, pageType?, layoutRef?
// REMOVE_PAGE      pageId
// SPLIT_PAGE       pageId(원본), destinationPageId(신규), newTitle, capabilityIds, pageType?, layoutRef?
// SET_PAGE_TYPE    pageId, pageType
// SET_LAYOUT       pageId, layoutRef
// SET_FEATURE      pageId, featureKey, featureEnabled
// ADD_NAVIGATION   navigationRule
// ADD_FLOW         flow
// ASSIGN_FLOW      flowId, pageId, actionId
public record PagePlanOperation(
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
        String reason
) {
    // 기존 7필드 호출부 및 외부 클라이언트와의 소스 호환용 편의 생성자.
    public PagePlanOperation(
            PagePlanOperationType type,
            String pageId,
            String otherPageId,
            String newTitle,
            String capabilityId,
            String destinationPageId,
            String reason
    ) {
        this(type, pageId, otherPageId, newTitle, capabilityId, destinationPageId,
                null, null, null, null, null, null, null, null, null, reason);
    }
}
