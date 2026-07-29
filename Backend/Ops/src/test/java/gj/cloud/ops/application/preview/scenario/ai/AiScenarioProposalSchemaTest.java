package gj.cloud.ops.application.preview.scenario.ai;

import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiScenarioProposalSchemaTest {

    @Test
    void openAiSdkCanGenerateStrictStructuredOutputSchema() {
        assertThat(ResponseCreateParams.builder()
                .model("schema-test")
                .input("{}")
                .text(AiScenarioProposal.class)
                .build()).isNotNull();
    }
}
