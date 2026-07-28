package gj.cloud.ops.application.preview.analysis;

import java.util.List;

// auto-preview-design/01-blueprint-schema.md의 Block Instance 축소판. Registry가 없으므로
// componentRef의 contractVersion/implementationVersion pin, bindingRefs는 두지 않는다 — 지금
// 렌더러가 필요한 정보는 capabilityIds만으로 충분하다(method/path/searchParam 등은 이미 Capability
// 안에 있음). DTO로 전송/저장하지 않고 PreviewComposeArtifactBuilder 내부에서만 쓴다.
public record Block(
        String instanceId,
        // "login-form" | "resource-table" | "detail-panel" | "create-edit-modal" | "delete-confirm-modal" | "dashboard-view"
        String componentId,
        // "page.content" | "page.main" | "page.aside" | "page.overlay"
        String slot,
        List<String> capabilityIds,
        // create-edit-modal 두 인스턴스(생성/수정)를 구분하는 용도로만 쓴다. 그 외 컴포넌트는 항상 null.
        String mode,
        // Direction Recovery Change Request Increment 4 detail 계열 — 이 Block이 활성화됐을 때(예:
        // 행 선택) 같은 페이지의 다른 Block(주로 같은 Slot을 두고 다투는 대안) 자리를 대신 차지한다는
        // 표시(그 Block의 instanceId). null이면 지금까지처럼 독립적으로 존재. SlotCardinalityValidator가
        // Slot Cardinality를 셀 때 이 Block은 세지 않고 replaces가 가리키는 Block이 실제로 존재하는지만
        // 확인한다(full-detail-page가 "list" Block 자리를 대신하는 것이 첫 사용처).
        String replaces
) {
    public Block(String instanceId, String componentId, String slot, List<String> capabilityIds, String mode) {
        this(instanceId, componentId, slot, capabilityIds, mode, null);
    }
}
