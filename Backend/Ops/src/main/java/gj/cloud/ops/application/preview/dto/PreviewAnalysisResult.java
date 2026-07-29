package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PlanningSource;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioDiagnostic;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceUnderstanding;

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
        // 실행 가능한 페이지 계획의 정본. PageDraft는 FALLBACK_CRUD 및 구버전 요청 호환용으로 유지한다.
        List<PagePlan> pagePlans,
        // Workflow Composition Phase 2 §22 7번(수직 슬라이스)으로 가는 첫 조각 — RuleBasedFlowGenerator가
        // pagePlans로부터 결정론적으로 만든 것 중 FlowBlueprintValidator/ApiBindingValidator를 통과한
        // 항목만 담는다(검증 실패분은 조용히 드랍되고 warnings에 사유가 남는다, §16 안전 폴백과 동일 원칙).
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings,
        List<UnresolvedField> unresolved,
        List<String> warnings,
        List<String> evidenceRefs,
        // 인증된 요청에 토큰을 실제로 어떻게 실어 보낼지(§9) — LOGIN 응답으로 받은 값을 나머지 모든
        // 보호된 요청에 동일하게 적용해야 하므로 Capability 하나가 아니라 문서 전체에 하나만 존재한다.
        AuthStrategy authStrategy,
        // Direction Recovery Change Request §17 — 이 pages가 어떻게 만들어졌는지 항상 명시적으로
        // 리포트한다. FALLBACK_CRUD를 SERVICE_AWARE인 것처럼 보여주면 안 된다.
        GenerationMode generationMode,
        // Scenario-first Runtime v3 — UI보다 먼저 확정되는 서비스 의미와 실행 시나리오.
        ServiceUnderstanding serviceUnderstanding,
        List<CompiledScenario> scenarios,
        List<ScenarioDiagnostic> scenarioDiagnostics,
        PreviewMode previewMode,
        PlanningSource scenarioPlanningSource,
        String scenarioPromptVersion
) {
}
