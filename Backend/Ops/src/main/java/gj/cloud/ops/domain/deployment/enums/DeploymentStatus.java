package gj.cloud.ops.domain.deployment.enums;

// D.6 배포 상태 머신 그대로.
// QUEUED→CLONING→UPLOADING→VALIDATING→BUILDING(여기까지 실패 시 기존 서비스 완전 유지, 다운타임 없음)
// →SWAPPING→HEALTH_CHECKING(이후 실패 시 ROLLING_BACK→ROLLED_BACK, 수초 다운타임)
// →ROUTING→SUCCEEDED
public enum DeploymentStatus {
    QUEUED,
    CLONING,
    UPLOADING,
    VALIDATING,
    BUILDING,
    SWAPPING,
    HEALTH_CHECKING,
    ROUTING,
    SUCCEEDED,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK,
    // 사용자가 명시적으로 "내리기"를 실행해 컨테이너를 중지/제거하는 상태 머신 밖의 흐름 — 실패/롤백과
    // 달리 배포 자체는 성공했었고, 사용자 의도로 내려간 것임을 구분하기 위함. STOPPING은 비동기로
    // 처리되는 동안(SSH+포트 정리) 잠깐 거치는 중간 상태, STOPPED이 최종 상태.
    STOPPING,
    STOPPED
}
