package gj.cloud.ops.application.deployment.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiComposeReviewerTest {

    @Test
    void redactsMappingAndListStyleSecretEnvironmentValues() {
        String compose = """
                services:
                  api:
                    environment:
                      API_TOKEN: mapping-secret
                      - DB_PASSWORD=list-secret
                      PUBLIC_NAME: gamjabox
                """;

        String redacted = AiComposeReviewer.redactSecrets(compose);

        assertThat(redacted)
                .contains("API_TOKEN: <REDACTED>")
                .contains("- DB_PASSWORD=<REDACTED>")
                .contains("PUBLIC_NAME: gamjabox")
                .doesNotContain("mapping-secret", "list-secret");
    }
}
