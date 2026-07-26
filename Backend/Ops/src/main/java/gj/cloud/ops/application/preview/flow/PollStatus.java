package gj.cloud.ops.application.preview.flow;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §9 "Runtime behavior"가
// 명시한 상태 집합. 아직 이 값을 실제로 만들어내거나 소비하는 실행기가 없다(FlowExecutor, §22
// 우선순위 6번) — WP-1~3과 같은 패턴으로 모델만 먼저 문서화해둔다.
public enum PollStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CANCELLED
}
