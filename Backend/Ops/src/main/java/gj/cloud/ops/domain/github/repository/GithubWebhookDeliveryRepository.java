package gj.cloud.ops.domain.github.repository;

import gj.cloud.ops.domain.github.entity.GithubWebhookDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubWebhookDeliveryRepository extends JpaRepository<GithubWebhookDeliveryEntity, String> {
}
