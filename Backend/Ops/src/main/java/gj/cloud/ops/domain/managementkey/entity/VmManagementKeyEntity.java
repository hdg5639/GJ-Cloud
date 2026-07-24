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

    // SEC-011: 최초 SSH 연결 시 TOFU(Trust On First Use)로 캡처해 저장 — 이후 연결은 이 값과 정확히
    // 일치할 때만 허용한다(VmSshSessionFactory 참고). 키가 회전되면(재프로비저닝) null로 초기화되어
    // 재생성된 VM에서 다시 캡처된다.
    @Column(name = "ssh_host_key_fingerprint")
    private String sshHostKeyFingerprint;

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

    // OPS-SEC-004: REVOKE_PENDING/REVOKED/ORPHANED 상태의 키는 재사용하지 않고 새 키쌍으로 교체할 때 사용 (재프로비저닝 대응)
    // SEC-011: 재프로비저닝된 VM은 호스트 키 자체가 새로 생성되므로 기존 지문을 초기화해 재캡처되게 한다.
    public VmManagementKeyEntity withRotatedKey(String publicKey, String encryptedPrivateKey) {
        return this.toBuilder()
                .publicKey(publicKey)
                .encryptedPrivateKey(encryptedPrivateKey)
                .status(KeyStatus.ACTIVE)
                .sshHostKeyFingerprint(null)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public VmManagementKeyEntity withHostKeyFingerprint(String fingerprint) {
        return this.toBuilder().sshHostKeyFingerprint(fingerprint).updatedAt(LocalDateTime.now()).build();
    }
}
