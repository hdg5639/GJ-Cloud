package gj.cloud.ops.domain.preview.entity;

import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import gj.cloud.ops.domain.preview.enums.CustomScenarioVisibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomScenarioEntityTest {

    @Test
    void movesThroughDraftValidationActivationAndArchiveWithoutLosingSemanticSource() {
        CustomScenarioEntity scenario = CustomScenarioEntity.generating(
                "service-1",
                "user-1",
                "멤버 초대 검증",
                "초대 후 PENDING 상태를 확인",
                "로그인하고 조직을 골라 멤버를 초대해줘",
                CustomScenarioVisibility.TEAM
        );

        scenario.markDraft("{\"id\":\"invite-member\"}");
        scenario.markValidating();
        scenario.markValidated();
        scenario.activate();

        assertThat(scenario.getStatus()).isEqualTo(CustomScenarioStatus.ACTIVE);
        assertThat(scenario.getNaturalLanguageSource()).contains("조직을 골라");
        assertThat(scenario.getScenarioDefinitionJson()).contains("invite-member");
        assertThat(scenario.getVisibility()).isEqualTo(CustomScenarioVisibility.TEAM);

        scenario.archive();
        assertThat(scenario.getStatus()).isEqualTo(CustomScenarioStatus.ARCHIVED);
    }

    @Test
    void successfulRevalidationCanRestoreAnActiveScenario() {
        CustomScenarioEntity scenario = CustomScenarioEntity.generating(
                "service-1", "user-1", "test", null, "test scenario",
                CustomScenarioVisibility.PRIVATE);
        scenario.markDraft("{}");
        scenario.markValidated();
        scenario.activate();

        scenario.markValidating();
        scenario.completeRevalidation(true);

        assertThat(scenario.getStatus()).isEqualTo(CustomScenarioStatus.ACTIVE);
    }
}
