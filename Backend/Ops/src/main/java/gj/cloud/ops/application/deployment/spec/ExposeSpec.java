package gj.cloud.ops.application.deployment.spec;

// enabled로 외부(Cloudflare 도메인) 노출 대상을 명시적으로 구분 (D-3 예시). DB/Redis 등은 기본값 false로 둬야 함.
public record ExposeSpec(boolean enabled, String protocol, String healthCheckPath) {
}
