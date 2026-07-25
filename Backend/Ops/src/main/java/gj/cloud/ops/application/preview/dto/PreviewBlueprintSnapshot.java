package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.RegistryStatus;

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
        PreviewAnalyzeRequest.Purpose purpose
) {
}
