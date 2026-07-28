package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/08-compatibility-rules.md §7. SECURITY_BLOCK은 아직 CompatibilityValidator가
// 판정하는 규칙 중 대상이 없어 정의만 해두고 실제로 배정하지는 않는다.
public enum CompatibilitySeverity {
    INFO,
    WARNING,
    ERROR,
    SECURITY_BLOCK
}
