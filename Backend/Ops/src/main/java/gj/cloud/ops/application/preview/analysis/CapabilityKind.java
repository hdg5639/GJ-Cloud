package gj.cloud.ops.application.preview.analysis;

// GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md §7.1 — CapabilityType(6종 CRUD/AUTH)은
// 편의 분류로 남기고, capability의 진짜 정체성은 이 kind가 담당한다. vm.start/invite 같은 커맨드형
// 오퍼레이션은 CapabilityType 어디에도 억지로 끼워 넣지 않고 COMMAND로 표현한다(type()은 null).
public enum CapabilityKind {
    QUERY,
    MUTATION,
    COMMAND,
    AUTH,
    METRIC,
    EVENT_STREAM,
    FILE_TRANSFER,
    WORKFLOW
}
