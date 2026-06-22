package gj.cloud.user.domain.profile.entity;

import gj.cloud.user.domain.plan.enums.PlanType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserProfileEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String email;

    private String nickname;

    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType planType;

    @Builder.Default
    @Column(nullable = false)
    private boolean suspended = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static UserProfileEntity createDefault(String userId, String email) {
        return UserProfileEntity.builder()
                .userId(userId)
                .email(email)
                .planType(PlanType.FREE)
                .suspended(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend() {
        this.suspended = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.suspended = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePlanType(PlanType planType) {
        this.planType = planType;
        this.updatedAt = LocalDateTime.now();
    }
}
