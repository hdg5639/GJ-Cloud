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
}
