package gj.cloud.ops.application.deployment.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.RepoConfig;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.application.vmclient.VmAutomationClient;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentExecutorValidationTest {
    enum Entry { MANUAL, TARGET, RETRY, AUTOMATIC, MANAGED }

    @Mock private VmServiceClient vmServiceClient;
    @Mock private VmAutomationClient vmAutomationClient;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private DeploymentLockService lockService;
    @Mock private AesGcmCipher cipher;
    @Mock private SshCommandExecutor sshCommandExecutor;
    @Mock private TaskExecutor deploymentTaskExecutor;
    @Spy private ComposeValidator composeValidator = new ComposeValidator();
    @InjectMocks private DeploymentExecutor executor;

    private final RepoConfig repository = new RepoConfig("https://github.com/example/app", "main", null, null, null);
    private final DeploymentTargetEntity target = DeploymentTargetEntity.builder()
            .id("target").vmId("vm").ownerUserId("user").ownerEmail("user@example.test").build();
    private final VmContextResponse context = new VmContextResponse("vm", "user", "192.0.2.1", "RUNNING", "OWNER", List.of("DEPLOY"));

    @ParameterizedTest
    @EnumSource(Entry.class)
    void summarizesThreeReasonsAndRemainingCountBeforeAnyDeploymentSideEffects(Entry entry) {
        authorize(entry);
        String compose = """
                services:
                  app:
                    image: nginx
                    privileged: true
                    network_mode: host
                    pid: host
                    ipc: host
                    cap_add: [SYS_ADMIN]
                """;
        assertThatThrownBy(() -> enqueue(entry, compose))
                .isInstanceOfSatisfying(OpsException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OpsErrorCode.INVALID_COMPOSE);
                    assertThat(error.getMessage()).contains("privileged", "host 네트워크", "pid: host", "외 2건")
                            .doesNotContain("ipc: host", "cap_add");
                });
        verifyNoInteractions(deploymentRepository, lockService, cipher, sshCommandExecutor, deploymentTaskExecutor);
    }

    @Test
    void syntaxErrorPreservesLocationWithoutEchoingSecretsToResponseOrLog() {
        authorize(Entry.MANUAL);
        Logger logger = (Logger) LoggerFactory.getLogger(DeploymentExecutor.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            assertThatThrownBy(() -> enqueue(Entry.MANUAL, "services:\n  app:\n    environment: [PASSWORD=private-fixture\n"))
                    .hasMessageContaining("YAML 파싱 오류")
                    .hasMessageContaining("행")
                    .hasMessageNotContaining("private-fixture")
                    .hasMessageNotContaining("PASSWORD");
            assertThat(captured.list).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).isEqualTo("compose 검증 실패 (1건)"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void rejectsPermissionBeforeDisclosingValidationDetails() {
        when(vmServiceClient.getContext("fixture", "vm")).thenReturn(
                new VmContextResponse("vm", "user", "192.0.2.1", "RUNNING", "MEMBER", List.of()));
        assertThatThrownBy(() -> enqueue(Entry.MANUAL, "invalid"))
                .isInstanceOfSatisfying(OpsException.class, error -> assertThat(error.getErrorCode()).isEqualTo(OpsErrorCode.FORBIDDEN));
        verifyNoInteractions(composeValidator, deploymentRepository, lockService, cipher, deploymentTaskExecutor);
    }

    private void authorize(Entry entry) {
        if (entry == Entry.AUTOMATIC) when(vmAutomationClient.getContext("vm", "user", "user@example.test")).thenReturn(context);
        else if (entry != Entry.MANAGED) when(vmServiceClient.getContext("fixture", "vm")).thenReturn(context);
    }

    private void enqueue(Entry entry, String compose) {
        ComposeArtifact artifact = new ComposeArtifact(compose, List.of(), List.of(), List.of(), List.of(), SourceType.RAW_COMPOSE);
        switch (entry) {
            case MANUAL -> executor.enqueue("fixture", "vm", repository, artifact);
            case TARGET -> executor.enqueueForTarget("fixture", "vm", target, repository, artifact);
            case RETRY -> executor.enqueueRetryForTarget("fixture", "vm", target, repository, artifact);
            case AUTOMATIC -> executor.enqueueAutomatic(target, repository, artifact, "revision");
            case MANAGED -> executor.enqueueManagedForTarget("worker", "192.0.2.1", target, repository, artifact);
        }
    }
}
