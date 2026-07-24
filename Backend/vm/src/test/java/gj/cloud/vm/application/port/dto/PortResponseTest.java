package gj.cloud.vm.application.port.dto;

import gj.cloud.vm.domain.port.entity.VmPortEntity;
import gj.cloud.vm.domain.port.enums.Protocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortResponseTest {

    @Test
    void includesPersistentDeploymentTargetId() {
        VmPortEntity port = VmPortEntity.createPublic(
                        UUID.randomUUID(),
                        8080,
                        Protocol.HTTP,
                        "web",
                        "portfolio",
                        "cloudflare-record")
                .withDeployment("target-1", "deployment-2");

        PortResponse response = PortResponse.of(port, List.of(), "gamjabox.cloud");

        assertThat(response.fullDomain()).isEqualTo("portfolio.gamjabox.cloud");
        assertThat(response.deploymentId()).isEqualTo("deployment-2");
        assertThat(response.deploymentAppId()).isEqualTo("target-1");
    }
}
