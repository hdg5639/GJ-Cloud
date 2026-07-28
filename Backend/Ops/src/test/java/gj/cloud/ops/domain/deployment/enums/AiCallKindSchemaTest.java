package gj.cloud.ops.domain.deployment.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallKindSchemaTest {

    @Test
    void previewGenerationLogConstraintContainsEveryApplicationKind() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("schema.sql must be available on the test classpath").isNotNull();
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int migrationStart = schema.indexOf(
                    "ALTER TABLE ai_preview_generation_log\n"
                            + "    DROP CONSTRAINT IF EXISTS chk_ai_preview_generation_log_kind;");

            assertThat(migrationStart)
                    .as("existing ai_preview_generation_log constraint must be migrated")
                    .isGreaterThanOrEqualTo(0);

            String migration = schema.substring(migrationStart);
            for (AiCallKind kind : AiCallKind.values()) {
                assertThat(migration)
                        .as("chk_ai_preview_generation_log_kind must allow %s", kind)
                        .contains("'" + kind.name() + "'");
            }
        }
    }
}
