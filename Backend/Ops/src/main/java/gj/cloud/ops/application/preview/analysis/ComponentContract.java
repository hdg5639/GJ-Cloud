package gj.cloud.ops.application.preview.analysis;

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
        boolean handlesSecrets
) {
}
