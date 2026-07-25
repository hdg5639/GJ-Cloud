package gj.cloud.ops.application.preview.ai;

// operation 종류마다 실제로 쓰는 필드가 다르다 — 그 외 필드는 항상 null.
// RENAME_PAGE: pageId, newTitle
// MERGE_PAGES: pageId(유지되는 쪽), otherPageId(합쳐진 뒤 제거됨)
// MOVE_CAPABILITY: capabilityId, destinationPageId
public record PagePlanOperation(
        PagePlanOperationType type,
        String pageId,
        String otherPageId,
        String newTitle,
        String capabilityId,
        String destinationPageId,
        String reason
) {
}
