package gj.cloud.ops.application.deployment.routing;

public record ComposeRouterRoute(
        String serviceName,
        String routePath,
        String upstream,
        int containerPort,
        Integer hostPort,
        boolean root,
        boolean stripPrefix,
        String source,
        String confidence,
        // "PREFIX"(경로 기반, 기본) | "DOMAIN"(호스트 기반). DOMAIN이면 customSubdomain으로 라우팅한다.
        String mode,
        // DOMAIN 모드일 때 서비스 전용 서브도메인 라벨. PREFIX 모드에서는 null.
        String customSubdomain
) {
    public static final String MODE_PREFIX = "PREFIX";
    public static final String MODE_DOMAIN = "DOMAIN";

    public boolean isDomain() {
        return MODE_DOMAIN.equals(mode);
    }
}
