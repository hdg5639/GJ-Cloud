package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.global.ssh.CommandResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentCommandLogPayloadTest {

    @Test
    void redactsCommonSecretsAndKeepsUsefulCommandContext() {
        CommandResult result = new CommandResult(
                1,
                "building layer\nPOSTGRES_PASSWORD=plain-password\nTOKEN=plain-token\n",
                "Authorization: Bearer secret-value\nghs_1234567890abcdefghijkl\nDockerfile line 9");

        DeploymentCommandLogPayload payload =
                DeploymentCommandLogPayload.from("docker-build", "api", result, 1234);

        assertThat(payload.operation()).isEqualTo("docker-build");
        assertThat(payload.subject()).isEqualTo("api");
        assertThat(payload.exitStatus()).isEqualTo(1);
        assertThat(payload.durationMs()).isEqualTo(1234);
        assertThat(payload.stdout())
                .contains("POSTGRES_PASSWORD=[REDACTED]")
                .contains("TOKEN=[REDACTED]")
                .doesNotContain("plain-password", "plain-token");
        assertThat(payload.stderr())
                .contains("Authorization: Bearer [REDACTED]")
                .contains("[REDACTED_GITHUB_TOKEN]")
                .contains("Dockerfile line 9")
                .doesNotContain("secret-value");
    }

    @Test
    void retainsTheTailAndMarksTruncatedOutput() {
        String output = "prefix-marker" + "x".repeat(DeploymentCommandLogPayload.MAX_OUTPUT_CHARS);

        DeploymentCommandLogPayload payload = DeploymentCommandLogPayload.from(
                "docker-build", "api", new CommandResult(0, output, ""), 10);

        assertThat(payload.stdoutTruncated()).isTrue();
        assertThat(payload.stdout()).hasSize(DeploymentCommandLogPayload.MAX_OUTPUT_CHARS);
        assertThat(payload.stdout()).doesNotContain("prefix-marker");
    }
}
