package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/05-capability-taxonomy.md §6 Automation Policy 그대로.
public enum AutomationPolicy {
    AUTO_SAFE,
    USER_INITIATED,
    EXPLICIT_CONFIRMATION,
    TYPED_CONFIRMATION,
    DISABLED_IN_AUTO_TEST
}
