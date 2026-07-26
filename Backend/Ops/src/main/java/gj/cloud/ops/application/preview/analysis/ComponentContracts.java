package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.List;
import java.util.Map;

// 고정 런타임 컴포넌트(Frontend/portal/components/preview-runtime/*.tsx, 배포 코드생성 템플릿
// PreviewComposeArtifactBuilder에 동일하게 이식됨)의 Contract 카탈로그. Registry Entry가 아니라 정적
// 상수 — 아직 등록/승격/버전 개념이 없다. PreviewBlockResolver가 만드는 Block이 이 카탈로그를
// 어기지 않는지 ComponentContractComplianceTest가 검증한다.
public final class ComponentContracts {

    // 10개를 넘어서면서 Map.of(...)의 고정 인자 오버로드(최대 10쌍) 한도를 넘어 Map.ofEntries로 전환.
    public static final Map<String, ComponentContract> ALL = Map.ofEntries(
            Map.entry("login-form", new ComponentContract(
                    "login-form", "PATTERN",
                    List.of(CapabilityType.LOGIN), false,
                    List.of("IDLE", "SUBMITTING", "ERROR", "SUCCESS"),
                    List.of("page.content"), false, true, List.of(),
                    // 계열 없음 — Variant 대체 후보가 없는 단독 컴포넌트.
                    null, List.of())),
            Map.entry("resource-table", new ComponentContract(
                    "resource-table", "PATTERN",
                    List.of(CapabilityType.LIST), false,
                    List.of("LOADING", "EMPTY", "ERROR", "SUCCESS"),
                    List.of("page.main"), false, false, List.of(),
                    "list", List.of())),
            // Direction Recovery Change Request §9.1 — resource-table과 같은 계열(list)의 두 번째
            // Variant. Capability/Slot 요구조건은 resource-table과 완전히 동일해 BlueprintCompiler가
            // purpose(PRODUCT_LIKE)에 따라 그냥 갈아끼울 수 있다.
            Map.entry("resource-card-grid", new ComponentContract(
                    "resource-card-grid", "PATTERN",
                    List.of(CapabilityType.LIST), false,
                    List.of("LOADING", "EMPTY", "ERROR", "SUCCESS"),
                    List.of("page.main"), false, false, List.of(),
                    "list", List.of(Purpose.PRODUCT_LIKE))),
            Map.entry("detail-panel", new ComponentContract(
                    "detail-panel", "PATTERN",
                    List.of(CapabilityType.DETAIL), false,
                    List.of("LOADING", "ERROR", "SUCCESS"),
                    List.of("page.aside"), false, false, List.of(),
                    "detail", List.of())),
            // Direction Recovery Change Request §9.2 — detail-panel과 같은 계열(detail)의 두 번째
            // Variant. Capability 요구조건은 동일하지만, 목록 대신 전체 폭을 차지해야 해서 실제 Slot이
            // page.aside가 아니라 page.main이다(BlueprintCompiler.SLOT_OVERRIDE가 이 componentId로
            // 바꿀 때 Block.slot도 함께 교체 — Block.replaces="list"로 목록 자리를 대신함).
            Map.entry("full-detail-page", new ComponentContract(
                    "full-detail-page", "PATTERN",
                    List.of(CapabilityType.DETAIL), false,
                    List.of("LOADING", "ERROR", "SUCCESS"),
                    List.of("page.main"), false, false, List.of(),
                    "detail", List.of(Purpose.PRODUCT_LIKE))),
            Map.entry("create-edit-modal", new ComponentContract(
                    "create-edit-modal", "PATTERN",
                    List.of(CapabilityType.CREATE, CapabilityType.UPDATE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, true, List.of(),
                    "create-edit", List.of())),
            // Direction Recovery Change Request §9.3 — create-edit-modal과 같은 계열(create/edit)의
            // 두 번째 Variant. Capability/Slot 요구조건은 동일해 BlueprintCompiler가 purpose
            // (PRODUCT_LIKE)에 따라 그냥 갈아끼울 수 있다.
            Map.entry("form-drawer", new ComponentContract(
                    "form-drawer", "PATTERN",
                    List.of(CapabilityType.CREATE, CapabilityType.UPDATE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, true, List.of(),
                    "create-edit", List.of(Purpose.PRODUCT_LIKE))),
            Map.entry("delete-confirm-modal", new ComponentContract(
                    "delete-confirm-modal", "PATTERN",
                    List.of(CapabilityType.DELETE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, false, List.of(),
                    "destructive", List.of())),
            // Direction Recovery Change Request §9.4 — delete-confirm-modal과 같은 계열(destructive)의
            // 두 번째 Variant. Capability/Slot 요구조건은 동일해 BlueprintCompiler가 purpose(ADMIN)에
            // 따라 그냥 갈아끼울 수 있다.
            Map.entry("typed-confirm-modal", new ComponentContract(
                    "typed-confirm-modal", "PATTERN",
                    List.of(CapabilityType.DELETE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, false, List.of(),
                    "destructive", List.of(Purpose.ADMIN))),
            Map.entry("dashboard-view", new ComponentContract(
                    "dashboard-view", "PAGE_FEATURE",
                    List.of(CapabilityType.LIST), true,
                    List.of("LOADING", "PARTIAL_ERROR", "SUCCESS"),
                    List.of("page.content"), false, false, List.of(),
                    "dashboard", List.of())),
            // Direction Recovery Change Request §9.5 — dashboard-view와 같은 계열(dashboard)의 두 번째
            // Variant. Capability/Slot 요구조건은 동일해 BlueprintCompiler가 purpose(PRODUCT_LIKE)에
            // 따라 그냥 갈아끼울 수 있다. operational-status-dashboard(§9.5의 세 번째 후보)는 METRIC
            // capability 추출기가 아직 없어 이번엔 만들지 않는다.
            Map.entry("recent-activity-dashboard", new ComponentContract(
                    "recent-activity-dashboard", "PAGE_FEATURE",
                    List.of(CapabilityType.LIST), true,
                    List.of("LOADING", "PARTIAL_ERROR", "SUCCESS"),
                    List.of("page.content"), false, false, List.of(),
                    "dashboard", List.of(Purpose.PRODUCT_LIKE))),
            // Direction Recovery Change Request §9.6 "Minimum command and action components" 중
            // 가장 단순한 것으로 메커니즘부터 증명한다 — COMMAND capability(vm.start 등)는 type()이
            // 항상 null이라 acceptedCapabilityTypes가 아니라 acceptedCapabilityKinds로 받는다.
            Map.entry("quick-action-button-group", new ComponentContract(
                    "quick-action-button-group", "PATTERN",
                    List.of(), true,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.actions"), false, false, List.of(CapabilityKind.COMMAND),
                    // 계열 없음 — purpose별 두 번째 Variant는 아직 없음(§22 4번 우선순위 후보).
                    null, List.of()))
    );

    private ComponentContracts() {
    }
}
