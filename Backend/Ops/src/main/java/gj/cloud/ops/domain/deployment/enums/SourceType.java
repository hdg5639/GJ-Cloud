package gj.cloud.ops.domain.deployment.enums;

// D-1(기본 템플릿)/D-3(AI 자동생성)만 DeploymentSpec을 거치고, D-2(사용자 지정)는 곧바로 RAW_COMPOSE로 ComposeArtifact에 감싸짐 (1.3절)
public enum SourceType {
    TEMPLATE_SPEC,
    AI_SPEC,
    RAW_COMPOSE,
    // Auto Preview(GamjaBox_2.0_Key_Features.md 1단계) — Git 저장소 없이 Ops가 그 자리에서 생성한
    // Vite+React 프로젝트. repositoryUrl/branch가 비어있는 DeploymentTargetEntity로 표현된다.
    AUTO_PREVIEW
}
