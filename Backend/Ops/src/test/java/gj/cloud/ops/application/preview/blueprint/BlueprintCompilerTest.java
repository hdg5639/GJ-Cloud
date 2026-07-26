package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintCompilerTest {

    @Test
    void productLikeSwapsResourceTableToResourceCardGrid() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(
                        new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                        new Block("detail", "detail-panel", "page.aside", List.of("vms.detail"), null)));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE);

        List<Block> compiledBlocks = compiled.get("vms-page");
        assertThat(compiledBlocks.get(0).componentId()).isEqualTo("resource-card-grid");
        assertThat(compiledBlocks.get(1).componentId()).isEqualTo("detail-panel");
    }

    @Test
    void otherPurposesLeaveResourceTableUnchanged() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(new Block("list", "resource-table", "page.main", List.of("vms.list"), null)));

        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.API_TEST).get("vms-page").get(0).componentId())
                .isEqualTo("resource-table");
        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.ADMIN).get("vms-page").get(0).componentId())
                .isEqualTo("resource-table");
        assertThat(BlueprintCompiler.compile(pageBlocks, null).get("vms-page").get(0).componentId())
                .isEqualTo("resource-table");
    }

    @Test
    void otherComponentIdsAreNeverSwapped() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "auth-login", List.of(new Block("login", "login-form", "page.content", List.of("auth.login"), null)));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE);

        assertThat(compiled.get("auth-login").get(0).componentId()).isEqualTo("login-form");
    }

    @Test
    void productLikeSwapsDashboardViewToRecentActivityDashboard() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "dashboard", List.of(new Block("dashboard", "dashboard-view", "page.content", List.of("vms.list"), null)));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE);

        assertThat(compiled.get("dashboard").get(0).componentId()).isEqualTo("recent-activity-dashboard");
    }

    @Test
    void otherPurposesLeaveDashboardViewUnchanged() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "dashboard", List.of(new Block("dashboard", "dashboard-view", "page.content", List.of("vms.list"), null)));

        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.API_TEST).get("dashboard").get(0).componentId())
                .isEqualTo("dashboard-view");
        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.ADMIN).get("dashboard").get(0).componentId())
                .isEqualTo("dashboard-view");
        assertThat(BlueprintCompiler.compile(pageBlocks, null).get("dashboard").get(0).componentId())
                .isEqualTo("dashboard-view");
    }

    @Test
    void adminSwapsDeleteConfirmModalToTypedConfirmModal() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(
                        new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                        new Block("delete", "delete-confirm-modal", "page.overlay", List.of("vms.delete"), null)));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.ADMIN);

        List<Block> compiledBlocks = compiled.get("vms-page");
        assertThat(compiledBlocks.get(0).componentId()).isEqualTo("resource-table");
        assertThat(compiledBlocks.get(1).componentId()).isEqualTo("typed-confirm-modal");
    }

    @Test
    void nonAdminPurposesLeaveDeleteConfirmModalUnchanged() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(new Block("delete", "delete-confirm-modal", "page.overlay", List.of("vms.delete"), null)));

        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.API_TEST).get("vms-page").get(0).componentId())
                .isEqualTo("delete-confirm-modal");
        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE).get("vms-page").get(0).componentId())
                .isEqualTo("delete-confirm-modal");
    }

    @Test
    void productLikeSwapsBothCreateAndUpdateCreateEditModalToFormDrawer() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(
                        new Block("create", "create-edit-modal", "page.overlay", List.of("vms.create"), "CREATE"),
                        new Block("update", "create-edit-modal", "page.overlay", List.of("vms.update"), "UPDATE")));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE);

        List<Block> compiledBlocks = compiled.get("vms-page");
        assertThat(compiledBlocks.get(0).componentId()).isEqualTo("form-drawer");
        assertThat(compiledBlocks.get(0).mode()).isEqualTo("CREATE");
        assertThat(compiledBlocks.get(1).componentId()).isEqualTo("form-drawer");
        assertThat(compiledBlocks.get(1).mode()).isEqualTo("UPDATE");
    }

    @Test
    void nonProductLikePurposesLeaveCreateEditModalUnchanged() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(new Block("create", "create-edit-modal", "page.overlay", List.of("vms.create"), "CREATE")));

        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.API_TEST).get("vms-page").get(0).componentId())
                .isEqualTo("create-edit-modal");
        assertThat(BlueprintCompiler.compile(pageBlocks, Purpose.ADMIN).get("vms-page").get(0).componentId())
                .isEqualTo("create-edit-modal");
    }

    @Test
    void handlesMultiplePagesIndependently() {
        Map<String, List<Block>> pageBlocks = Map.of(
                "vms-page", List.of(new Block("list", "resource-table", "page.main", List.of("vms.list"), null)),
                "tags-page", List.of(new Block("list", "resource-table", "page.main", List.of("tags.list"), null)));

        Map<String, List<Block>> compiled = BlueprintCompiler.compile(pageBlocks, Purpose.PRODUCT_LIKE);

        assertThat(compiled.get("vms-page").get(0).componentId()).isEqualTo("resource-card-grid");
        assertThat(compiled.get("tags-page").get(0).componentId()).isEqualTo("resource-card-grid");
    }
}
