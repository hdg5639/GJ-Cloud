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

@Entity
@Table(name = "github_webhook_deliveries")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubWebhookDeliveryEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static GithubWebhookDeliveryEntity received(String id, String eventType, String status) {
        return GithubWebhookDeliveryEntity.builder()
                .id(id)
                .eventType(eventType)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public GithubWebhookDeliveryEntity withStatus(String nextStatus) {
        return GithubWebhookDeliveryEntity.builder()
                .id(id)
                .eventType(eventType)
                .status(nextStatus)
                .createdAt(createdAt)
                .build();
    }
}
