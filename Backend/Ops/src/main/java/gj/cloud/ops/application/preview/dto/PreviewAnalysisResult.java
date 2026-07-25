package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;

import java.util.List;

// AiGenerationResult(D-3)와 같은 형태를 그대로 재사용 — status가 READY가 아니면 근거 없이 완전한 결과를
// 지어내지 않고 unresolved 사유를 그대로 보여준다. Phase A는 AI를 부르지 않으므로 CONFLICT/INVALID_RESPONSE는
// 아직 발생하지 않지만(Phase B에서 사용), 프론트가 두 파이프라인을 같은 방식으로 다룰 수 있도록 형태를 맞춘다.
public record PreviewAnalysisResult(
        GenerationStatus status,
        // 문서의 servers[].url — Phase D 배포 시 생성된 앱이 실제로 호출할 API 주소의 기본값으로 쓰인다.
        // 사용자가 그대로 쓸지 다른 주소로 바꿀지 확인할 수 있게 첫 번째 값만 넘기지 않고 전체를 넘긴다.
        List<String> apiServerUrls,
        List<Capability> capabilities,
        List<PageDraft> pages,
        List<UnresolvedField> unresolved,
        List<String> warnings,
        List<String> evidenceRefs,
        // 인증된 요청에 토큰을 실제로 어떻게 실어 보낼지(§9) — LOGIN 응답으로 받은 값을 나머지 모든
        // 보호된 요청에 동일하게 적용해야 하므로 Capability 하나가 아니라 문서 전체에 하나만 존재한다.
        AuthStrategy authStrategy
) {
}
