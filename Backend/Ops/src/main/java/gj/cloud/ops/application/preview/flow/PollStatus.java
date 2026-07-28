package gj.cloud.ops.application.preview.flow;

// Workflow Composition Phase 2 §9의 Polling 상태 집합. Portal과 배포 Runtime의 진행 UI가
// 같은 상태 이름을 사용해 실행 중·성공·실패·timeout·사용자 취소를 구분한다.
public enum PollStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CANCELLED
}
