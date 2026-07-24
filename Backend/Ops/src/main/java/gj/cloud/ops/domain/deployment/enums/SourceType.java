package gj.cloud.ops.domain.deployment.enums;

// D-1(기본 템플릿)/D-3(AI 자동생성)만 DeploymentSpec을 거치고, D-2(사용자 지정)는 곧바로 RAW_COMPOSE로 ComposeArtifact에 감싸짐 (1.3절)
public enum SourceType {
    TEMPLATE_SPEC,
    AI_SPEC,
    RAW_COMPOSE
}
