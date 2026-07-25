package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCardinalityValidatorTest {

    @Test
    void fullCrudListDetailHasNoViolations() {
        List<Block> blocks = List.of(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                new Block("detail", "detail-panel", "page.aside", List.of("vms.detail"), null),
                new Block("create", "create-edit-modal", "page.overlay", List.of("vms.create"), "CREATE"),
                new Block("update", "create-edit-modal", "page.overlay", List.of("vms.update"), "UPDATE"),
                new Block("delete", "delete-confirm-modal", "page.overlay", List.of("vms.delete"), null)
        );

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.LIST_DETAIL, blocks)).isEmpty();
    }

    @Test
    void listOnlyResourceListHasNoViolations() {
        List<Block> blocks = List.of(
                new Block("list", "resource-table", "page.main", List.of("tags.list"), null)
        );

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.RESOURCE_LIST, blocks)).isEmpty();
    }

    @Test
    void missingRequiredPageMainIsAViolation() {
        List<Block> blocks = List.of();

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.RESOURCE_LIST, blocks))
                .anyMatch(v -> v.contains("page.main"));
    }

    @Test
    void duplicateExactlyOneSlotIsAViolation() {
        List<Block> blocks = List.of(
                new Block("list1", "resource-table", "page.main", List.of("vms.list"), null),
                new Block("list2", "resource-table", "page.main", List.of("tags.list"), null)
        );

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.RESOURCE_LIST, blocks))
                .anyMatch(v -> v.contains("page.main"));
    }

    @Test
    void slotNotProvidedBySkeletonIsAViolation() {
        List<Block> blocks = List.of(
                new Block("login", "login-form", "page.content", List.of("auth.login"), null),
                new Block("stray", "resource-table", "page.main", List.of("vms.list"), null)
        );

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.AUTH_PAGE, blocks))
                .anyMatch(v -> v.contains("page.main") && v.contains("제공하지 않음"));
    }

    @Test
    void zeroOrOneAndZeroOrMoreSlotsAllowAbsence() {
        List<Block> blocks = List.of(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null)
        );

        assertThat(SlotCardinalityValidator.validate(PageSkeletonType.LIST_DETAIL, blocks)).isEmpty();
    }
}
