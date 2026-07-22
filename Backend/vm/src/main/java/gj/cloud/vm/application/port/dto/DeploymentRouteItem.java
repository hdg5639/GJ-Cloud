package gj.cloud.vm.application.port.dto;

// Ops 서비스 ComposeArtifact.exposedRoutes 항목과 1:1 대응 (gamjabox 기획 1.5절 규칙1).
// nickname이 배포 라우트 동기화의 매칭 키로 쓰임 (vm_ports.nickname과 대조).
public record DeploymentRouteItem(
        String serviceName,
        int port,
        String protocol,
        String visibility,
        String nickname,
        // PRO 전용 커스텀 서브도메인 — null/빈 값이면 기존처럼 자동 생성(PortServiceImpl.addDeploymentRoute 참고)
        String customSubdomain
) {
}
