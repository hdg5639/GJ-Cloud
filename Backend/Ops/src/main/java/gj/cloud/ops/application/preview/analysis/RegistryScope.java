package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/09-registry-lifecycle.md §4 — Status와 별개 축(§5). MVP에는 Organization/
// Shared 승격 경로가 없어 PROJECT/SYSTEM 두 값만 실제로 쓰인다.
public enum RegistryScope {
    PROJECT,
    ORGANIZATION,
    SHARED,
    SYSTEM
}
