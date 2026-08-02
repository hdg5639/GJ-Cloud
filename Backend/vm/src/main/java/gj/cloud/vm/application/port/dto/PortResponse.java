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
        String deploymentId,
        // 재배포마다 바뀌는 deploymentId와 달리 배포 대상 ID는 유지된다.
        // 대상 카드에서 현재 연결된 실제 포트를 안정적으로 묶는 키로 사용한다.
        String deploymentAppId,
        // 사용자가 수동 생성한 CNAME을 어느 배포 대상 카드에 표시할지 나타내는 별도 연결 ID.
        String linkedDeploymentTargetId
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
                entity.getDeploymentId(),
                entity.getDeploymentAppId(),
                entity.getLinkedDeploymentTargetId()
        );
    }
}
