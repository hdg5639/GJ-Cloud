package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/08-compatibility-rules.md §5 — "Rule은 Blueprint를 자동 수정하지 않고 Finding을
// 반환한다." severity로 사용처가 갈린다: ERROR는 09-registry-lifecycle.md §11의 "Schema·Build 통과 시
// PROJECT+VALIDATED" 게이트를 막고, 그 외(WARNING 등)는 PreviewAnalysisResult.warnings에만 표시된다.
public record CompatibilityFinding(CompatibilitySeverity severity, String message) {
}
