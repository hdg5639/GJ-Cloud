package gj.cloud.vm.application.port.dto;

import gj.cloud.vm.domain.port.entity.VmPortEntity;

import java.time.LocalDateTime;
import java.util.List;

public record PortResponse(
        String id,
        int port,
        String protocol,
        String visibility,
        String subdomain,
        String fullDomain,
        List<String> accessEmails,
        LocalDateTime createdAt
) {
    public static PortResponse of(VmPortEntity entity, List<String> emails, String baseDomain) {
        return new PortResponse(
                entity.getId().toString(),
                entity.getPort(),
                entity.getProtocol().name(),
                entity.getVisibility().name(),
                entity.getSubdomain(),
                entity.getSubdomain() + "." + baseDomain,
                emails,
                entity.getCreatedAt()
        );
    }
}
