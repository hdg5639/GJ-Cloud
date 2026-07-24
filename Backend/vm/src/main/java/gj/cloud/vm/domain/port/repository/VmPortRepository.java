package gj.cloud.vm.domain.port.repository;

import gj.cloud.vm.domain.port.entity.VmPortEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmPortRepository extends ReactiveCrudRepository<VmPortEntity, UUID> {

    Flux<VmPortEntity> findAllByVmId(UUID vmId);

    // 배포(Ops)가 생성한 포트만 대상으로 함 — 사용자가 수동으로 추가한 포트는 배포 라우트 동기화에서 건드리지 않음
    Flux<VmPortEntity> findAllByVmIdAndDeploymentIdIsNotNull(UUID vmId);

    Flux<VmPortEntity> findAllByVmIdAndDeploymentAppId(UUID vmId, String deploymentAppId);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE vm_id = :vmId")
    Mono<Long> countByVmId(UUID vmId);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE vm_id = :vmId AND port = :port")
    Mono<Long> countByVmIdAndPort(UUID vmId, int port);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE vm_id = :vmId AND nickname = :nickname")
    Mono<Long> countByVmIdAndNickname(UUID vmId, String nickname);

    @Query("SELECT COUNT(*) FROM vm_ports WHERE subdomain = :subdomain")
    Mono<Long> countBySubdomain(String subdomain);
}
