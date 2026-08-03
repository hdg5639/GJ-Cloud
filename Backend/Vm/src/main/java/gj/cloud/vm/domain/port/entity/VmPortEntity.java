package gj.cloud.vm.domain.port.entity;

import gj.cloud.vm.domain.port.enums.Protocol;
import gj.cloud.vm.domain.port.enums.Visibility;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("vm_ports")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VmPortEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("vm_id")
    private UUID vmId;

    private int port;
    private Protocol protocol;
    private Visibility visibility;
    private String nickname;
    private String subdomain;

    @Column("cf_dns_record_id")
    private String cfDnsRecordId;

    @Column("cf_app_id")
    private String cfAppId;

    @Column("cf_policy_id")
    private String cfPolicyId;

    @Column("created_at")
    private LocalDateTime createdAt;

    // 배포(Ops)가 생성한 포트인지 추적 — null이면 사용자가 수동으로 추가한 포트 (배포 라우트 동기화 대상 아님)
    @Column("deployment_id")
    private String deploymentId;

    @Column("deployment_app_id")
    private String deploymentAppId;

    // 수동 등록 포트를 배포 대상 UI에 묶는 표시용 연결. 자동 배포 소유권/정리 기준과 분리한다.
    @Column("linked_deployment_target_id")
    private String linkedDeploymentTargetId;

    public static VmPortEntity createPublic(UUID vmId, int port, Protocol protocol,
                                            String nickname, String subdomain, String cfDnsRecordId) {
        return VmPortEntity.builder()
                .id(UUID.randomUUID()).isNew(true)
                .vmId(vmId).port(port).protocol(protocol).visibility(Visibility.PUBLIC)
                .nickname(nickname).subdomain(subdomain).cfDnsRecordId(cfDnsRecordId)
                .createdAt(LocalDateTime.now()).build();
    }

    public static VmPortEntity createPrivate(UUID vmId, int port, Protocol protocol,
                                             String nickname, String subdomain, String cfDnsRecordId,
                                             String cfAppId, String cfPolicyId) {
        return VmPortEntity.builder()
                .id(UUID.randomUUID()).isNew(true)
                .vmId(vmId).port(port).protocol(protocol).visibility(Visibility.PRIVATE)
                .nickname(nickname).subdomain(subdomain).cfDnsRecordId(cfDnsRecordId)
                .cfAppId(cfAppId).cfPolicyId(cfPolicyId)
                .createdAt(LocalDateTime.now()).build();
    }

    public VmPortEntity withDeployment(String deploymentAppId, String deploymentId) {
        return this.toBuilder()
                .deploymentAppId(deploymentAppId)
                .deploymentId(deploymentId)
                .build();
    }

    public VmPortEntity withLinkedDeploymentTarget(String deploymentTargetId) {
        return this.toBuilder()
                .linkedDeploymentTargetId(deploymentTargetId)
                .build();
    }
}
