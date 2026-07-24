package gj.cloud.ops.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.service.AutoDeploymentService;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.domain.github.entity.GithubWebhookDeliveryEntity;
import gj.cloud.ops.domain.github.repository.GithubWebhookDeliveryRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
public class GithubWebhookService {

    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final GithubWebhookDeliveryRepository deliveryRepository;
    private final DeploymentTargetRepository targetRepository;
    private final AutoDeploymentService autoDeploymentService;

    public GithubWebhookService(
            @Value("${ops.github.webhook-secret:}") String webhookSecret,
            ObjectMapper objectMapper,
            GithubWebhookDeliveryRepository deliveryRepository,
            DeploymentTargetRepository targetRepository,
            AutoDeploymentService autoDeploymentService
    ) {
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
        this.deliveryRepository = deliveryRepository;
        this.targetRepository = targetRepository;
        this.autoDeploymentService = autoDeploymentService;
    }

    public void handle(String deliveryId, String eventType, String signature, byte[] body) {
        verifySignature(signature, body);
        if (deliveryId == null || deliveryId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new OpsException(OpsErrorCode.GITHUB_WEBHOOK_SIGNATURE_INVALID);
        }
        GithubWebhookDeliveryEntity delivery;
        try {
            delivery = deliveryRepository.saveAndFlush(
                    GithubWebhookDeliveryEntity.received(deliveryId, eventType, "PROCESSING"));
        } catch (DataIntegrityViolationException duplicate) {
            delivery = deliveryRepository.findById(deliveryId).orElse(null);
            if (delivery == null || !"FAILED".equals(delivery.getStatus())) {
                return;
            }
            delivery = deliveryRepository.saveAndFlush(delivery.withStatus("PROCESSING"));
        }

        try {
            if (!"push".equals(eventType)) {
                deliveryRepository.save(delivery.withStatus("IGNORED"));
                return;
            }
            JsonNode payload = objectMapper.readTree(body);
            if (payload.path("deleted").asBoolean()) {
                deliveryRepository.save(delivery.withStatus("IGNORED"));
                return;
            }
            long installationId = payload.path("installation").path("id").asLong();
            long repositoryId = payload.path("repository").path("id").asLong();
            String ref = payload.path("ref").asText();
            String revision = payload.path("after").asText();
            if (installationId == 0 || repositoryId == 0) {
                deliveryRepository.save(delivery.withStatus("IGNORED"));
                return;
            }

            List<DeploymentTargetEntity> targets =
                    targetRepository.findAllByGithubInstallationIdAndGithubRepositoryIdAndAutoDeployEnabledTrueAndActiveTrue(
                            installationId, repositoryId);
            targets.stream()
                    .filter(target -> ("refs/heads/" + target.getBranch()).equals(ref))
                    .forEach(target -> autoDeploymentService.request(target, revision));
            deliveryRepository.save(delivery.withStatus("SUCCEEDED"));
        } catch (Exception e) {
            try {
                deliveryRepository.save(delivery.withStatus("FAILED"));
            } catch (Exception statusError) {
                log.warn("GitHub webhook 실패 상태 저장 실패: deliveryId={}, error={}",
                        deliveryId, statusError.getMessage());
            }
            log.error("GitHub push webhook 처리 실패: deliveryId={}, error={}", deliveryId, e.getMessage());
            if (e instanceof OpsException opsException) {
                throw opsException;
            }
            throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
        }
    }

    private void verifySignature(String signature, byte[] body) {
        if (webhookSecret.isBlank() || signature == null || !signature.startsWith("sha256=")) {
            throw new OpsException(OpsErrorCode.GITHUB_WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII))) {
                throw new OpsException(OpsErrorCode.GITHUB_WEBHOOK_SIGNATURE_INVALID);
            }
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            throw new OpsException(OpsErrorCode.GITHUB_WEBHOOK_SIGNATURE_INVALID);
        }
    }
}
