package gj.cloud.ops.domain.systemworker.entity;

import gj.cloud.ops.domain.systemworker.enums.SystemWorkerRole;
import gj.cloud.ops.domain.systemworker.enums.SystemWorkerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_workers")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemWorkerEntity {
    @Id @Column(length = 36) private String id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true, length = 40) private SystemWorkerRole role;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "vm_id", nullable = false) private Integer vmId;
    @Column(length = 100) private String node;
    @Column(name = "internal_ip", length = 64) private String internalIp;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SystemWorkerStatus status;
    @Column(name = "provisioning_stage", length = 40) private String provisioningStage;
    @Column(name = "ssh_key_ref", nullable = false, length = 36) private String sshKeyRef;
    @Column(nullable = false) private int cores;
    @Column(name = "memory_mb", nullable = false) private int memoryMb;
    @Column(name = "disk_gb", nullable = false) private int diskGb;
    @Column(name = "last_health_check_at") private LocalDateTime lastHealthCheckAt;
    @Column(name = "last_error", columnDefinition = "TEXT") private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static SystemWorkerEntity provisioning(String name, int vmId, int cores, int memoryMb, int diskGb) {
        LocalDateTime now = LocalDateTime.now();
        String id = UUID.randomUUID().toString();
        return SystemWorkerEntity.builder().id(id).role(SystemWorkerRole.AUTO_PREVIEW).name(name).vmId(vmId)
                .status(SystemWorkerStatus.PROVISIONING).provisioningStage("REGISTERING").sshKeyRef(id)
                .cores(cores).memoryMb(memoryMb).diskGb(diskGb).createdAt(now).updatedAt(now).build();
    }

    public SystemWorkerEntity stage(String stage) {
        return toBuilder().provisioningStage(stage).updatedAt(LocalDateTime.now()).build();
    }
    public SystemWorkerEntity healthy(String node, String ip) {
        LocalDateTime now = LocalDateTime.now();
        return toBuilder().node(node).internalIp(ip).status(SystemWorkerStatus.ACTIVE).provisioningStage("READY")
                .lastHealthCheckAt(now).lastError(null).updatedAt(now).build();
    }
    public SystemWorkerEntity observed(SystemWorkerStatus status, String node, String ip, String error) {
        LocalDateTime now = LocalDateTime.now();
        return toBuilder().status(status).node(node == null ? this.node : node).internalIp(ip == null ? this.internalIp : ip)
                .lastHealthCheckAt(now).lastError(error).updatedAt(now).build();
    }
    public SystemWorkerEntity missing(String node) {
        LocalDateTime now = LocalDateTime.now();
        return toBuilder().status(SystemWorkerStatus.MISSING).node(node == null ? this.node : node).internalIp(null)
                .provisioningStage("MISSING").lastHealthCheckAt(now).lastError("Proxmox VM이 없습니다.")
                .updatedAt(now).build();
    }
    public SystemWorkerEntity failed(String error) {
        return toBuilder().status(SystemWorkerStatus.ERROR).provisioningStage("FAILED").lastError(error)
                .updatedAt(LocalDateTime.now()).build();
    }
    public SystemWorkerEntity reprovisioning() {
        return toBuilder().status(SystemWorkerStatus.PROVISIONING).provisioningStage("REGISTERING")
                .node(null).internalIp(null).lastError(null).updatedAt(LocalDateTime.now()).build();
    }
}
