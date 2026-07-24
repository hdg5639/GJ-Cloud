package gj.cloud.user.domain.sshkey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ssh_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SshKeyEntity {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(nullable = false, unique = true)
    private String fingerprint;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SshKeyEntity create(String userId, String publicKey, String fingerprint, String name) {
        return SshKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .publicKey(publicKey)
                .fingerprint(fingerprint)
                .name(name)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
