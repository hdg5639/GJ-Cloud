package gj.cloud.ops.domain.github.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "github_installations")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubInstallationEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "account_login", nullable = false)
    private String accountLogin;

    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static GithubInstallationEntity create(
            Long installationId, String userId, String accountLogin, String accountType
    ) {
        return GithubInstallationEntity.builder()
                .id(UUID.randomUUID().toString())
                .installationId(installationId)
                .userId(userId)
                .accountLogin(accountLogin)
                .accountType(accountType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public GithubInstallationEntity refreshed(String nextAccountLogin, String nextAccountType) {
        return GithubInstallationEntity.builder()
                .id(id)
                .installationId(installationId)
                .userId(userId)
                .accountLogin(nextAccountLogin)
                .accountType(nextAccountType)
                .createdAt(createdAt)
                .build();
    }
}
