package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.planning.model.PagePlan;

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
        // Workflow Composition Phase 2 Change Request WP-1 — PageDraft에서 결정론적으로 파생한 풍부한
        // 페이지 모델(route/pageType/features 등). PageDraft는 여전히 실제 Block 리졸브·컴파일·배포
        // 경로의 정본이고(폴백 유지), pagePlans는 다음 작업(Navigation/FlowBlueprint)이 소비할 자리를
        // 미리 마련해둔 것 — 지금은 어떤 소비자도 없다.
        List<PagePlan> pagePlans,
        List<UnresolvedField> unresolved,
        List<String> warnings,
        List<String> evidenceRefs,
        // 인증된 요청에 토큰을 실제로 어떻게 실어 보낼지(§9) — LOGIN 응답으로 받은 값을 나머지 모든
        // 보호된 요청에 동일하게 적용해야 하므로 Capability 하나가 아니라 문서 전체에 하나만 존재한다.
        AuthStrategy authStrategy,
        // Direction Recovery Change Request §17 — 이 pages가 어떻게 만들어졌는지 항상 명시적으로
        // 리포트한다. FALLBACK_CRUD를 SERVICE_AWARE인 것처럼 보여주면 안 된다.
        GenerationMode generationMode
) {
}
