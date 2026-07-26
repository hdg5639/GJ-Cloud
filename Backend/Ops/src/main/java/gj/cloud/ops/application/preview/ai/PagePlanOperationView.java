package gj.cloud.ops.application.preview.ai;

// Direction Recovery Change Request Increment 5(2부) — Plan Review UI. AiPagePlanner.propose가
// PagePlanOperation마다 원본 후보 페이지 기준으로 개별 검증해 valid/validationError를 매긴다.
// id는 인덱스 기반 문자열(예: "0")로, 사용자가 apply 단계에서 선택한 서브셋을 식별하는 용도로만 쓴다.
public record PagePlanOperationView(
        String id,
        PagePlanOperationType type,
        String pageId,
        String otherPageId,
        String newTitle,
        String capabilityId,
        String destinationPageId,
        String reason,
        boolean valid,
        // valid=false일 때만 값이 있음(구조 검증 또는 Compatibility 검증 실패 사유).
        String validationError
) {
}
