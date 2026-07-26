package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.RegistryStatus;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;

import java.util.List;
import java.util.Map;

// auto-preview-design/01-blueprint-schema.md §14 MVP 축소판 — Patch·Revision·Component Contract
// 버전 Pin 없이, 배포 시점에 실제로 무엇을 배포했는지(capabilities/pages/authStrategy + 리졸브된
// Block 배치)를 배포 기록과 함께 그대로 저장해 나중에 재분석 없이 조회할 수 있게 한다. 편집·재적용
// 대상이 아닌 읽기 전용 스냅샷 — Patch API·baseRevision 충돌 처리는 만들지 않는다.
// status는 09-registry-lifecycle.md §11 MVP 관계 — CompatibilityValidator에 ERROR Finding이 하나도
// 없으면 VALIDATED, 있으면 DRAFT. scope는 항상 PROJECT라(승격 경로가 아직 없음) 별도 필드로 두지 않는다.
public record PreviewBlueprintSnapshot(
        String apiBaseUrl,
        List<Capability> capabilities,
        List<PageDraft> pages,
        AuthStrategy authStrategy,
        Map<String, List<Block>> pageBlocks,
        RegistryStatus status,
        // Direction Recovery Change Request Increment 4 — pageBlocks의 componentId가 이미
        // BlueprintCompiler로 이 목적에 맞게 컴파일된 결과라, 어떤 목적으로 컴파일됐는지도 함께 남긴다.
        PreviewAnalyzeRequest.Purpose purpose,
        // Workflow Composition Phase 2 Change Request WP-6 — AC-9 "Deterministic compilation"을
        // 나중에 감사할 수 있도록 배포 시점에 실제로 쓰인 PagePlan/FlowBlueprint/ApiBinding을 함께
        // 저장한다. 배포 요청(PreviewDeployRequest)이 이 값을 직접 보내지 않고, pages/capabilities로부터
        // PreviewDeployController가 analyze()와 동일한 방식(PagePlanMapper+RuleBasedFlowGenerator)으로
        // 다시 계산해서 채운다 — 아직 배포된 정적 아티팩트가 flows를 실제로 실행하지는 않는다(WP-8의
        // 다음 조각, PreviewComposeArtifactBuilder에 FlowExecutor를 미러링하는 작업으로 명시적으로 미룸).
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings
) {
}
