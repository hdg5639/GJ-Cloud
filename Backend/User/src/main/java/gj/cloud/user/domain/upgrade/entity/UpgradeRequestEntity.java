package gj.cloud.user.domain.upgrade.entity;

import gj.cloud.user.domain.upgrade.enums.UpgradeRequestStatus;
import gj.cloud.user.domain.upgrade.enums.UpgradeRequestType;
import gj.cloud.user.domain.plan.enums.PlanType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "upgrade_requests")
public class UpgradeRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpgradeRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType targetPlanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpgradeRequestStatus status;

    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
    private String reviewedBy;

    public static UpgradeRequestEntity createRequest(String userId, UpgradeRequestType type, PlanType targetPlanType) {
        UpgradeRequestEntity entity = new UpgradeRequestEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTargetPlanType(targetPlanType);
        entity.setStatus(UpgradeRequestStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    public void approve(String adminId) {
        this.status = UpgradeRequestStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(String adminId, String reason) {
        this.status = UpgradeRequestStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
        this.reason = reason;
    }
}
