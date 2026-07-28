package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/09-registry-lifecycle.md §3 — MVP는 GENERATED를 별도로 안 두고
// DRAFT→VALIDATED 전이를 배포 요청 한 번에 동기적으로 판정한다(§11 MVP 관계). VERIFIED/OFFICIAL/
// DEPRECATED/REVOKED는 사용 이벤트 축적·수동 승인이 필요해 아직 아무 것도 이 상태로 전이하지 않는다 —
// System Scope 6개 고정 Component만 예외적으로 처음부터 OFFICIAL(ComponentRegistry 참고).
public enum RegistryStatus {
    DRAFT,
    GENERATED,
    VALIDATED,
    VERIFIED,
    OFFICIAL,
    DEPRECATED,
    REVOKED
}
