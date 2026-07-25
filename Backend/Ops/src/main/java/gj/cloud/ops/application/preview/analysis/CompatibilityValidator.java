package gj.cloud.ops.application.preview.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// auto-preview-design/08-compatibility-rules.md — Component/Slot Contract만으로 표현하기 어려운
// 조합 제약을 확인한다. 문서가 정의하는 심각도(INFO~SECURITY_BLOCK)·Phase·조건 DSL 전체를 만들지
// 않는다 — Registry·Feature Schema가 아직 없어 §6의 대부분 규칙(Cursor Pagination, Bulk Select,
// Social Login 등)은 걸릴 대상 자체가 없다. 지금 실제로 판별 가능한 것만 고정 규칙으로 둔다:
// Slot Cardinality(SlotCardinalityValidator), Component Contract 준수(ComponentContracts), 그리고
// §6 "로그인" 규칙 4 "최소 한 개 Username-like Field와 Password Field 필요"의 축소판. 결과는
// PreviewAnalysisResult.warnings에 그대로 합쳐진다 — Rule이 Blueprint를 자동 수정하지 않고 Finding만
// 반환한다는 §5 원칙 그대로, 사용자가 보고 판단하게 한다.
public final class CompatibilityValidator {

    public static List<String> validate(PageDraft page, List<Block> blocks, List<Capability> capabilities) {
        List<String> findings = new ArrayList<>();
        findings.addAll(SlotCardinalityValidator.validate(page.skeleton(), blocks));

        for (Block block : blocks) {
            ComponentContract contract = ComponentContracts.ALL.get(block.componentId());
            if (contract == null) {
                findings.add("등록되지 않은 componentId: " + block.componentId());
                continue;
            }
            if (!contract.acceptedSurfaces().contains(block.slot())) {
                findings.add(block.componentId() + "는 " + block.slot() + " Slot을 받지 않음");
            }
            for (String capabilityId : block.capabilityIds()) {
                findById(capabilities, capabilityId)
                        .filter(c -> !contract.acceptedCapabilityTypes().contains(c.type()))
                        .ifPresent(c -> findings.add(block.componentId() + "는 " + c.type() + " 타입을 받지 않음"));
            }

            if (block.componentId().equals("login-form")) {
                block.capabilityIds().stream()
                        .findFirst()
                        .flatMap(id -> findById(capabilities, id))
                        .filter(login -> !login.fields().isEmpty()
                                && login.fields().stream().noneMatch(CompatibilityValidator::isPasswordLikeField))
                        .ifPresent(login -> findings.add(
                                "\"" + page.title() + "\" 페이지의 로그인 폼에 비밀번호로 보이는 필드가 없습니다(필드: "
                                        + String.join(", ", login.fields()) + ")."));
            }
        }
        return findings;
    }

    // PreviewComposeArtifactBuilder 템플릿/Frontend api.ts의 isPasswordLikeField와 동일한 규칙.
    private static boolean isPasswordLikeField(String name) {
        String lower = name.toLowerCase();
        return lower.contains("password") || lower.equals("pw") || lower.equals("pwd");
    }

    private static Optional<Capability> findById(List<Capability> capabilities, String id) {
        return capabilities.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    private CompatibilityValidator() {
    }
}
