package gj.cloud.ops.domain.managementkey.entity;

import gj.cloud.ops.domain.managementkey.enums.KeyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vm_management_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
@AllArgsConstructor
public class VmManagementKeyEntity {

    @Id
    private String id;

    @Column(name = "vm_id", nullable = false, unique = true)
    private String vmId;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    // AES-256-GCM으로 암호화된 개인키 (PEM/OpenSSH 포맷 원문을 암호화)
    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static VmManagementKeyEntity create(String vmId, String publicKey, String encryptedPrivateKey) {
        LocalDateTime now = LocalDateTime.now();
        return VmManagementKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .vmId(vmId)
                .publicKey(publicKey)
                .encryptedPrivateKey(encryptedPrivateKey)
                .status(KeyStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public VmManagementKeyEntity withStatus(KeyStatus status) {
        return this.toBuilder().status(status).updatedAt(LocalDateTime.now()).build();
    }
}
