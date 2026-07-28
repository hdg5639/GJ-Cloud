package gj.cloud.ops.application.preview.analysis;

// GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md §17 — "이 결과가 어떻게 만들어졌는지"를
// 항상 명시적으로 리포트한다. FALLBACK_CRUD를 SERVICE_AWARE인 것처럼 보여주면 안 된다는 게 이 필드의
// 핵심 목적이다.
public enum GenerationMode {
    // 서비스 설명·purpose를 AI가 실제로 해석해 페이지 구성에 반영함(Increment 3 AiPagePlanner 이후).
    SERVICE_AWARE,
    // AI 없이 purpose 기반 규칙만으로 비-범용 페이지 구성을 만듦(RuleBasedPagePlanGenerator).
    RULE_BASED,
    // purpose 없이(또는 계획 실패로) 리소스-경로 그대로 그룹핑하는 기존 PageDraftGenerator로 대체됨.
    FALLBACK_CRUD
}
