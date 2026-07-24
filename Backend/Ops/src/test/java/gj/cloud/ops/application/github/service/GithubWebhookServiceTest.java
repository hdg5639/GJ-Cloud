package gj.cloud.ops.application.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.service.AutoDeploymentService;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.domain.github.entity.GithubWebhookDeliveryEntity;
import gj.cloud.ops.domain.github.repository.GithubWebhookDeliveryRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GithubWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

    private final GithubWebhookDeliveryRepository deliveryRepository =
            mock(GithubWebhookDeliveryRepository.class);
    private final DeploymentTargetRepository targetRepository =
            mock(DeploymentTargetRepository.class);
    private final AutoDeploymentService autoDeploymentService =
            mock(AutoDeploymentService.class);

    private GithubWebhookService service;

    @BeforeEach
    void setUp() {
        service = new GithubWebhookService(
                SECRET,
                new ObjectMapper(),
                deliveryRepository,
                targetRepository,
                autoDeploymentService);
        when(deliveryRepository.saveAndFlush(any(GithubWebhookDeliveryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.save(any(GithubWebhookDeliveryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatchesOnlyTheConfiguredBranchWithTheExactPushSha() throws Exception {
        byte[] body = payload("refs/heads/main").getBytes(StandardCharsets.UTF_8);
        DeploymentTargetEntity mainTarget = DeploymentTargetEntity.builder()
                .id("target-main")
                .branch("main")
                .active(true)
                .autoDeployEnabled(true)
                .build();
        DeploymentTargetEntity developTarget = DeploymentTargetEntity.builder()
                .id("target-develop")
                .branch("develop")
                .active(true)
                .autoDeployEnabled(true)
                .build();
        when(targetRepository
                .findAllByGithubInstallationIdAndGithubRepositoryIdAndAutoDeployEnabledTrueAndActiveTrue(
                        123L, 456L))
                .thenReturn(List.of(mainTarget, developTarget));

        service.handle("delivery-1", "push", signature(body), body);

        verify(autoDeploymentService).request(mainTarget, REVISION);
        verify(autoDeploymentService, never()).request(developTarget, REVISION);
    }

    @Test
    void rejectsPayloadWhenHmacSignatureDoesNotMatch() {
        byte[] body = payload("refs/heads/main").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.handle(
                "delivery-1", "push", "sha256=0000", body))
                .isInstanceOfSatisfying(OpsException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OpsErrorCode.GITHUB_WEBHOOK_SIGNATURE_INVALID));

        verifyNoInteractions(deliveryRepository, targetRepository, autoDeploymentService);
    }

    private String payload(String ref) {
        return """
                {
                  "ref": "%s",
                  "after": "%s",
                  "deleted": false,
                  "installation": {"id": 123},
                  "repository": {"id": 456}
                }
                """.formatted(ref, REVISION);
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
