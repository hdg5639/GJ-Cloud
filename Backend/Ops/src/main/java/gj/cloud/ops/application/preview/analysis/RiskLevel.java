package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/05-capability-taxonomy.md §5 위험도 enum 그대로.
public enum RiskLevel {
    SAFE,
    STATE_CHANGING,
    DESTRUCTIVE,
    IRREVERSIBLE,
    EXTERNAL_SIDE_EFFECT
}
