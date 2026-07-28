package gj.cloud.ops.application.preview.blueprint;

// Frontend blueprints/core/catalog.ts의 BlueprintCategory와 1:1로 맞춘 도메인 카테고리.
// ResourceCategoryClassifier가 리소스를 이 중 하나로 결정론적으로 분류하고, BlueprintPartSelector가
// 같은 카테고리를 선호하는 파츠를 고른다. 근거 없으면 분류하지 않는다(null → 기본 컴포넌트 폴백).
public enum BlueprintCategory {
    ADMIN,
    ANALYTICS,
    COMMERCE,
    CONTENT,
    CRM,
    INFRASTRUCTURE,
    OBSERVABILITY,
    PROJECT,
    SETTINGS,
    WORKFLOW
}
