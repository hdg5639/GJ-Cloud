package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.List;

// auto-preview-design/02-component-contract.md 축소판. Registry가 없어 contractVersion/
// implementationVersion Pin, Binding Role 요구, Slot Contract 참조는 아직 두지 않는다 — 지금 6개
// 고정 런타임 컴포넌트가 실제로 뭘 요구/제공하는지를 데이터로 고정해, PreviewBlockResolver가 만드는
// Block이 계약을 어기지 않는지 검증하는 용도로 우선 쓴다.
public record ComponentContract(
        String componentId,
        // Primitive~Product Recipe 중 Level 2 이상만 정식 Contract를 요구한다는 §2 결정 — 지금 6개는
        // 전부 Pattern(Level 4) 또는 Page Feature(Level 5)다.
        String componentType,           // "PATTERN" | "PAGE_FEATURE"
        // 이 컴포넌트가 바인딩될 수 있는 Capability 타입들(create-edit-modal처럼 여러 타입을 받을 수
        // 있음 — mode로 구분).
        List<CapabilityType> acceptedCapabilityTypes,
        // dashboard-view만 true(같은 타입의 Capability 여러 개를 동시에 받음). 나머지는 정확히 1개.
        boolean allowsMultipleCapabilities,
        List<String> requiredStates,
        List<String> acceptedSurfaces,
        boolean acceptsHtml,
        boolean handlesSecrets,
        // COMMAND capability는 type()이 항상 null이라 acceptedCapabilityTypes로 표현할 수 없다 —
        // kind 기반으로 받아들이는 capability를 여기 따로 선언한다. 기존 9개 Contract는 빈 리스트.
        List<CapabilityKind> acceptedCapabilityKinds,
        // Workflow Composition Phase 2 Change Request §13 WP-5 "resolve required component families" —
        // 같은 family 값을 가진 Contract들이 하나의 Variant 계열이다(같은 Slot·Capability 요구조건을
        // 공유, 서로 갈아끼울 수 있음). null이면 계열이 없는 단독 컴포넌트(지금은 login-form/
        // quick-action-button-group).
        String family,
        // 이 family 안에서 이 componentId가 선호되는 purpose 목록. BlueprintCompiler가 이 필드로
        // Variant를 고른다(예전엔 BlueprintCompiler 안에 Map<기본componentId, Map<Purpose,대체>>로
        // 하드코딩돼 있던 것 — Direction Recovery Change Request §10.2 "Retrieval order"가 예고한
        // 일반화). 계열마다 정확히 하나는 빈 리스트(그 어떤 purpose에도 안 걸리는 기본값)여야 한다.
        List<Purpose> preferredPurposes
) {
}
