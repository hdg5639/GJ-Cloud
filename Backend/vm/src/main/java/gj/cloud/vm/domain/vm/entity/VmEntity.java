package gj.cloud.vm.domain.vm.entity;

import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
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

@Table("vms")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VmEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("user_id")
    private String userId;

    private Integer vmid;
    private String name;

    @Column("plan_type")
    private PlanType planType;

    private VmStatus status;

    @Column("ssh_key_id")
    private String sshKeyId;

    @Column("internal_ip")
    private String internalIp;

    @Column("proxmox_task_id")
    private String proxmoxTaskId;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;

    private String subdomain;

    @Column("cf_dns_record_id")
    private String cfDnsRecordId;

    @Column("cf_app_id")
    private String cfAppId;

    @Column("cf_policy_id")
    private String cfPolicyId;

    public static VmEntity createPending(String userId, String name, PlanType planType, String sshKeyId) {
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return VmEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .userId(userId)
                .name(name)
                .planType(planType)
                .status(VmStatus.PENDING)
                .sshKeyId(sshKeyId)
                .subdomain("gj-" + shortUuid)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withStatus(VmStatus status) {
        return this.toBuilder().isNew(false).status(status).updatedAt(LocalDateTime.now()).build();
    }

    public VmEntity withVmidAndTaskId(int vmid, String proxmoxTaskId) {
        return this.toBuilder()
                .isNew(false)
                .vmid(vmid)
                .proxmoxTaskId(proxmoxTaskId)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withRunning(String internalIp) {
        return this.toBuilder()
                .isNew(false)
                .status(VmStatus.RUNNING)
                .internalIp(internalIp)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withFailed(String errorMessage) {
        return this.toBuilder()
                .isNew(false)
                .status(VmStatus.FAILED)
                .errorMessage(errorMessage)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withDeleted() {
        return this.toBuilder()
                .isNew(false)
                .status(VmStatus.DELETED)
                .deletedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withCloudflareIds(String cfDnsRecordId, String cfAppId, String cfPolicyId) {
        return this.toBuilder()
                .isNew(false)
                .cfDnsRecordId(cfDnsRecordId)
                .cfAppId(cfAppId)
                .cfPolicyId(cfPolicyId)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
