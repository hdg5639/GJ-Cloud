package gj.cloud.ops.application.preview.flow;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §17 보안 요구사항의 구체적
// 상한값. FlowBlueprintValidator가 이 값들을 참조해 검증 시점에 강제한다. 실제 실행 시점의 강제
// (폴링 타이머 준수, 타임아웃 취소, 페이지 이탈 시 중단 등)는 FlowExecutor(§22 우선순위 6번, 아직
// 없음)가 도입될 때 마저 쓴다.
public final class FlowExecutionPolicy {

    // "flow step count limit".
    public static final int MAX_STEPS = 20;
    // "timeout and retry limits" — 단일 step(WAIT/POLL)의 최대 타임아웃.
    public static final int MAX_TIMEOUT_SECONDS = 300;
    // "maximum poll count" — MIN_INTERVAL_MS와 MAX_TIMEOUT_SECONDS로부터 계산되는 poll 횟수의 상한.
    public static final int MAX_POLL_COUNT = 100;
    // POLL step의 최소 간격(§9 "enforce a maximum interval and timeout"). MAX_TIMEOUT_SECONDS를
    // 이 값으로 나누면 정확히 MAX_POLL_COUNT가 나오도록 맞춰져 있다(300s / 3000ms = 100).
    public static final int MIN_INTERVAL_MS = 3000;

    private FlowExecutionPolicy() {
    }
}
