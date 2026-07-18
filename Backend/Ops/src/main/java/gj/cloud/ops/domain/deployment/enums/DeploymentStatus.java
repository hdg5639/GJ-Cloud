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
    ROLLED_BACK
}
