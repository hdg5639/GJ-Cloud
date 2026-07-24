package gj.cloud.ops.application.deployment.spec;

// AI/사용자가 자유 문자열(buildCommand/startCommand)을 직접 생성하지 못하도록 허용목록으로 못박은 전략 값.
// DockerfileGenerator가 각 값을 고정된 argv 배열로만 변환하므로, 임의 셸 명령이 RUN/CMD로 들어갈 수 없다.
// build 단계와 run 단계가 이 enum을 공유하되, 실제로는 각자 의미 있는 부분집합만 사용함(검증은
// DeploymentSpecValidator에서 build/run 위치별로 허용값을 나눠 확인).
public enum BuildRunStrategy {
    NONE,
    COPY_SOURCE,
    DOCKERFILE,

    NPM_CI,
    NPM_INSTALL,
    NPM_BUILD,
    NPM_START,
    PNPM_INSTALL,
    PNPM_BUILD,
    PNPM_START,
    YARN_INSTALL,
    YARN_BUILD,
    YARN_START,

    MAVEN_PACKAGE,
    MAVEN_SPRING_BOOT_RUN,
    GRADLE_BUILD,
    GRADLE_BOOT_JAR,
    JAVA_JAR,

    PIP_INSTALL,
    UV_SYNC,
    GUNICORN,
    UVICORN,

    STATIC_SERVER
}
