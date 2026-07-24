package gj.cloud.ops.domain.deployment.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentStatusSchemaTest {

    @Test
    void deploymentStatusConstraintContainsEveryApplicationStatus() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("schema.sql must be available on the test classpath").isNotNull();
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (DeploymentStatus status : DeploymentStatus.values()) {
                assertThat(schema)
                        .as("chk_deployment_status must allow %s", status)
                        .contains("'" + status.name() + "'");
            }
            assertThat(schema).contains(
                    "ALTER TABLE deployments DROP CONSTRAINT IF EXISTS chk_deployment_status;");
        }
    }
}
