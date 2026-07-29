package gj.cloud.ops.application.preview.regression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegressionDataRedactorTest {

    @Test
    void redactsSecretsRecursivelyBeforeExecutionHistoryIsPersisted() {
        RegressionDataRedactor redactor = new RegressionDataRedactor();

        Object redacted = redactor.redact(Map.of(
                "headers", Map.of("Authorization", "Bearer secret"),
                "body", Map.of("email", "dev@example.com", "password", "plain"),
                "items", List.of(Map.of("accessToken", "token-value"))
        ));

        assertThat(redacted.toString())
                .contains("dev@example.com")
                .doesNotContain("Bearer secret", "plain", "token-value")
                .contains("••••••");
    }
}
