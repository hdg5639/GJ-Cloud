package gj.cloud.ops.application.preview.analysis;

import java.util.List;
import java.util.Map;

// 고정 런타임 컴포넌트(Frontend/portal/components/preview-runtime/*.tsx, 배포 코드생성 템플릿
// PreviewComposeArtifactBuilder에 동일하게 이식됨)의 Contract 카탈로그. Registry Entry가 아니라 정적
// 상수 — 아직 등록/승격/버전 개념이 없다. PreviewBlockResolver가 만드는 Block이 이 카탈로그를
// 어기지 않는지 ComponentContractComplianceTest가 검증한다.
public final class ComponentContracts {

    public static final Map<String, ComponentContract> ALL = Map.of(
            "login-form", new ComponentContract(
                    "login-form", "PATTERN",
                    List.of(CapabilityType.LOGIN), false,
                    List.of("IDLE", "SUBMITTING", "ERROR", "SUCCESS"),
                    List.of("page.content"), false, true),
            "resource-table", new ComponentContract(
                    "resource-table", "PATTERN",
                    List.of(CapabilityType.LIST), false,
                    List.of("LOADING", "EMPTY", "ERROR", "SUCCESS"),
                    List.of("page.main"), false, false),
            // Direction Recovery Change Request §9.1 — resource-table과 같은 계열(list)의 두 번째
            // Variant. Capability/Slot 요구조건은 resource-table과 완전히 동일해 BlueprintCompiler가
            // purpose(PRODUCT_LIKE)에 따라 그냥 갈아끼울 수 있다.
            "resource-card-grid", new ComponentContract(
                    "resource-card-grid", "PATTERN",
                    List.of(CapabilityType.LIST), false,
                    List.of("LOADING", "EMPTY", "ERROR", "SUCCESS"),
                    List.of("page.main"), false, false),
            "detail-panel", new ComponentContract(
                    "detail-panel", "PATTERN",
                    List.of(CapabilityType.DETAIL), false,
                    List.of("LOADING", "ERROR", "SUCCESS"),
                    List.of("page.aside"), false, false),
            "create-edit-modal", new ComponentContract(
                    "create-edit-modal", "PATTERN",
                    List.of(CapabilityType.CREATE, CapabilityType.UPDATE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, true),
            "delete-confirm-modal", new ComponentContract(
                    "delete-confirm-modal", "PATTERN",
                    List.of(CapabilityType.DELETE), false,
                    List.of("IDLE", "SUBMITTING", "ERROR"),
                    List.of("page.overlay"), false, false),
            "dashboard-view", new ComponentContract(
                    "dashboard-view", "PAGE_FEATURE",
                    List.of(CapabilityType.LIST), true,
                    List.of("LOADING", "PARTIAL_ERROR", "SUCCESS"),
                    List.of("page.content"), false, false)
    );

    private ComponentContracts() {
    }
}
