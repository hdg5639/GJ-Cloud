package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;

import java.util.List;

// AiGenerationResult(D-3)와 같은 형태를 그대로 재사용 — status가 READY가 아니면 근거 없이 완전한 결과를
// 지어내지 않고 unresolved 사유를 그대로 보여준다. Phase A는 AI를 부르지 않으므로 CONFLICT/INVALID_RESPONSE는
// 아직 발생하지 않지만(Phase B에서 사용), 프론트가 두 파이프라인을 같은 방식으로 다룰 수 있도록 형태를 맞춘다.
public record PreviewAnalysisResult(
        GenerationStatus status,
        List<Capability> capabilities,
        List<PageDraft> pages,
        List<UnresolvedField> unresolved,
        List<String> warnings,
        List<String> evidenceRefs
) {
}
