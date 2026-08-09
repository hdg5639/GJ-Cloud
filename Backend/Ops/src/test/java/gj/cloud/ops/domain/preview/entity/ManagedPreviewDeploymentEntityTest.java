package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.application.preview.managed.dto.ManagedPreviewResponse;
import gj.cloud.ops.domain.preview.enums.ManagedPreviewStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedPreviewDeploymentEntityTest {

    @Test
    void allocationUsesIsolatedRuntimeNamesAndPlanTtl() {
        ManagedPreviewDeploymentEntity preview = ManagedPreviewDeploymentEntity.allocate("user-1", "worker-1", 20001, 6);

        assertThat(preview.getContainerName()).startsWith("gamjabox-preview-");
        assertThat(preview.getComposeProjectName()).startsWith("gj_preview_");
        assertThat(preview.getSubdomain()).matches("preview-[a-f0-9]{12}");
        assertThat(preview.getStatus()).isEqualTo(ManagedPreviewStatus.ALLOCATED);
        assertThat(preview.getExpiresAt()).isEqualTo(preview.getCreatedAt().plusHours(6));
    }

    @Test
    void publicResponseDoesNotExposeWorkerInfrastructure() {
        var componentNames = Arrays.stream(ManagedPreviewResponse.class.getRecordComponents())
                .map(component -> component.getName()).toList();

        assertThat(componentNames).doesNotContain("workerId", "vmId", "node", "internalIp", "sshKeyRef", "internalPort");
    }
}
