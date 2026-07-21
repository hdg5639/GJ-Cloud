package gj.cloud.auth.domain.user.entity;

import gj.cloud.auth.domain.user.enums.UserRole;
import gj.cloud.auth.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static UserEntity create(String email, String encodedPassword) {
        return UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .password(encodedPassword)
                .role(UserRole.USER)
                .status(UserStatus.PENDING_VERIFICATION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void anonymizeAndDelete() {
        this.email = "deleted_" + this.id + "@deleted";
        this.password = "DELETED";
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }
}
