package gj.cloud.ops.application.deployment.spec;

// build.runtime과 run.runtime이 공유하는 카테고리. 빌드 시점에 쓰인 런타임과 최종 실행 시점에 쓰이는
// 런타임이 다를 수 있음을 표현하기 위해 분리(예: Node로 빌드하고 nginx로 서빙 → build.runtime=NODEJS, run.runtime=STATIC_SERVER).
public enum RuntimeKind {
    NODEJS,
    JAVA,
    PYTHON,
    DOCKERFILE,
    STATIC_SERVER,
    NONE
}
