package gj.cloud.ops.application.deployment.spec;

// SERVICE: 컨테이너가 계속 떠서 요청을 처리 (서버 프로세스가 있음)
// ARTIFACT_ONLY: 빌드 산출물(정적 파일 등)만 존재 — 자체 서버 프로세스가 없으면 STATIC_SERVER 같은 run 전략으로 서빙
public enum DeploymentMode {
    SERVICE,
    ARTIFACT_ONLY
}
