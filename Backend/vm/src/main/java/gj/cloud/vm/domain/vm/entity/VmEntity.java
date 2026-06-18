package gj.cloud.vm.domain.vm.entity;

import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.domain.vm.enums.VmStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("vms")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VmEntity {

    @Id
    private UUID id;

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

    public static VmEntity createPending(String userId, String name, PlanType planType, String sshKeyId) {
        return VmEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .planType(planType)
                .status(VmStatus.PENDING)
                .sshKeyId(sshKeyId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withStatus(VmStatus status) {
        return this.toBuilder().status(status).updatedAt(LocalDateTime.now()).build();
    }

    public VmEntity withVmidAndTaskId(int vmid, String proxmoxTaskId) {
        return this.toBuilder()
                .vmid(vmid)
                .proxmoxTaskId(proxmoxTaskId)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withRunning(String internalIp) {
        return this.toBuilder()
                .status(VmStatus.RUNNING)
                .internalIp(internalIp)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withFailed(String errorMessage) {
        return this.toBuilder()
                .status(VmStatus.FAILED)
                .errorMessage(errorMessage)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmEntity withDeleted() {
        return this.toBuilder()
                .status(VmStatus.DELETED)
                .deletedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
