package gj.cloud.vm.application.port.dto;

import gj.cloud.vm.domain.port.entity.VmPortEntity;

import java.time.LocalDateTime;
import java.util.List;

public record PortResponse(
        String id,
        int port,
        String protocol,
        String visibility,
        String nickname,
        String subdomain,
        String fullDomain,
        List<String> accessEmails,
        LocalDateTime createdAt,
        // 배포(자동배포 파이프라인)가 만든 포트인지 — null이면 사용자가 직접 추가한 포트.
        // 배포 "내리기" 시 어떤 포트를 선택 삭제 후보로 보여줄지 프론트가 이 필드로 구분한다.
        String deploymentId
) {
    public static PortResponse of(VmPortEntity entity, List<String> emails, String baseDomain) {
        return new PortResponse(
                entity.getId().toString(),
                entity.getPort(),
                entity.getProtocol().name(),
                entity.getVisibility().name(),
                entity.getNickname(),
                entity.getSubdomain(),
                entity.getSubdomain() + "." + baseDomain,
                emails,
                entity.getCreatedAt(),
                entity.getDeploymentId()
        );
    }
}
