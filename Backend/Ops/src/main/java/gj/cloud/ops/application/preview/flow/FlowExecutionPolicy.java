package gj.cloud.ops.application.preview.flow;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §17 보안 요구사항의 구체적
// 상한값. 지금은 FlowBlueprintValidator만 이 값을 참조하고, 실제 실행 시점의 강제(폴링 간격 준수,
// 타임아웃 취소 등)는 FlowExecutor(§22 우선순위 6번, 아직 없음)가 도입될 때 마저 쓴다.
public final class FlowExecutionPolicy {

    // "flow step count limit".
    public static final int MAX_STEPS = 20;
    // "timeout and retry limits" — 단일 step(WAIT/POLL)의 최대 타임아웃.
    public static final int MAX_TIMEOUT_SECONDS = 300;
    // "maximum poll count" — 최소 폴링 간격을 3초로 가정했을 때의 상한(실행기 도입 시 실제로 씀).
    public static final int MAX_POLL_COUNT = 100;

    private FlowExecutionPolicy() {
    }
}
