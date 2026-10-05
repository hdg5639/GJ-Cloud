package gj.cloud.ops.application.preview.scenario.ai;

import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiScenarioProposalSchemaTest {

    @Test
    void planningInputIncludesDocumentedOptionAndImageArrays() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var normalizer = new gj.cloud.ops.application.preview.analysis.OpenApiNormalizer(mapper,
                org.mockito.Mockito.mock(gj.cloud.ops.application.preview.analysis.OpenApiDocumentSecurityValidator.class));
        var evidence = normalizer.normalizeContent("""
                {"openapi":"3.0.3","info":{"title":"Commerce","version":"1"},"paths":{
                  "/products/{productId}":{"get":{"operationId":"productDetail","responses":{"200":{"content":{"application/json":{"schema":{
                    "type":"object","properties":{"data":{"type":"object","properties":{
                      "id":{"type":"string"},"images":{"type":"array","items":{"type":"string"}},
                      "optionGroups":{"type":"array","items":{"type":"object"}}
                    }}}
                  }}}}}}}
                }}
                """);
        var planner = new AiScenarioPlanner(org.mockito.Mockito.mock(com.openai.client.OpenAIClient.class),
                "model-test", mapper, org.mockito.Mockito.mock(gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository.class),
                new ScenarioProposalNormalizer());
        Object input = org.springframework.test.util.ReflectionTestUtils.invokeMethod(planner, "toInput", evidence,
                "Commerce", gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose.PRODUCT_LIKE, java.util.List.of());
        var json = mapper.valueToTree(input);
        assertThat(json.path("operations").get(0).path("responseFields").toString())
                .contains("data.images", "data.optionGroups", "data.id");
    }

    @Test
    void openAiSdkCanGenerateStrictStructuredOutputSchema() {
        assertThat(ResponseCreateParams.builder()
                .model("schema-test")
                .input("{}")
                .text(AiScenarioProposal.class)
                .build()).isNotNull();
    }
}
