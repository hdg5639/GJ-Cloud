package gj.cloud.ops.application.preview.flow;

// Workflow Composition Phase 2 §17 보안 상한. Backend Validator와 Portal/배포 FlowExecutor가
// 동일한 정책을 사용해 step 수, Polling 간격·횟수, timeout과 취소 가능성을 이중으로 강제한다.
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
    // 너무 긴 간격은 진행 UI를 사실상 멈춘 것처럼 만들고 timeout 의미를 약화시키므로 60초로 제한한다.
    public static final int MAX_INTERVAL_MS = 60_000;

    private FlowExecutionPolicy() {
    }
}
