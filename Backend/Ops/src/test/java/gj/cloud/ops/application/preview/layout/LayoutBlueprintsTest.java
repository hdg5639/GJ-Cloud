package gj.cloud.ops.application.preview.layout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §10 "Minimum initial layouts" —
// 7종이 전부 등록돼 있고 각자 최소 한 개 이상의 Slot을 선언하는지 확인한다.
class LayoutBlueprintsTest {

    @Test
    void registersAllSevenMinimumInitialLayouts() {
        assertThat(LayoutBlueprints.ALL.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                "auth-layout", "dashboard-layout", "resource-list-layout", "resource-detail-layout",
                "list-detail-layout", "workflow-layout", "settings-layout"));
    }

    @Test
    void everyLayoutIdMatchesItsMapKeyAndHasAtLeastOneSlot() {
        for (var entry : LayoutBlueprints.ALL.entrySet()) {
            assertThat(entry.getValue().id()).isEqualTo(entry.getKey());
            assertThat(entry.getValue().slots()).isNotEmpty();
        }
    }

    // §10 예시 JSON 그대로 옮긴 유일한 레이아웃 — page.main/page.aside 패턴과 Slot 이름 자체가 다르다.
    @Test
    void resourceDetailLayoutMatchesTheChangeRequestExample() {
        LayoutBlueprint layout = LayoutBlueprints.ALL.get("resource-detail-layout");

        assertThat(layout.slots()).extracting(LayoutSlot::name).containsExactly(
                "page.header", "page.summary", "page.primary", "page.secondary", "page.actions", "page.overlay");
    }

    @Test
    void everySlotNameWithinALayoutIsUnique() {
        for (LayoutBlueprint layout : LayoutBlueprints.ALL.values()) {
            List<String> names = layout.slots().stream().map(LayoutSlot::name).toList();
            assertThat(names).doesNotHaveDuplicates();
        }
    }
}
