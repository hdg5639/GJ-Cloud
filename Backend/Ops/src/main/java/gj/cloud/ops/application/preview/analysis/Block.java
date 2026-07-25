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
        String mode
) {
}
