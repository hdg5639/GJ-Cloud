package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// auto-preview-design/01-blueprint-schema.md 회귀 테스트 — PreviewPageRenderer.tsx/
// PreviewComposeArtifactBuilder에 하드코딩돼 있던 렌더링 규칙을 그대로 Block으로 옮겼는지 확인한다.
class PreviewBlockResolverTest {

    private final PreviewBlockResolver resolver = new PreviewBlockResolver();

    @Test
    void authPageProducesLoginFormBlockWhenLoginCapabilityExists() {
        Capability login = capability("auth.login", "auth", CapabilityType.LOGIN);
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of("auth.login"));

        List<Block> blocks = resolver.resolve(page, List.of(login));

        assertThat(blocks).containsExactly(
                new Block("login", "login-form", "page.content", List.of("auth.login"), null));
    }

    @Test
    void authPageProducesNoBlocksWhenLoginCapabilityMissing() {
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of());

        List<Block> blocks = resolver.resolve(page, List.of());

        assertThat(blocks).isEmpty();
    }

    @Test
    void dashboardCollectsAllListCapabilitiesIntoOneBlock() {
        Capability vmsList = capability("vms.list", "vms", CapabilityType.LIST);
        Capability tagsList = capability("tags.list", "tags", CapabilityType.LIST);
        PageDraft page = new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD,
                List.of("vms.list", "tags.list"));

        List<Block> blocks = resolver.resolve(page, List.of(vmsList, tagsList));

        assertThat(blocks).containsExactly(
                new Block("dashboard", "dashboard-view", "page.content", List.of("vms.list", "tags.list"), null));
    }

    @Test
    void defaultSkeletonWithAllCrudCapabilitiesProducesFiveBlocksInOrder() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        Capability update = capability("vms.update", "vms", CapabilityType.UPDATE);
        Capability delete = capability("vms.delete", "vms", CapabilityType.DELETE);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                List.of("vms.list", "vms.detail", "vms.create", "vms.update", "vms.delete"));

        List<Block> blocks = resolver.resolve(page, List.of(list, detail, create, update, delete));

        assertThat(blocks).containsExactly(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                new Block("detail", "detail-panel", "page.aside", List.of("vms.detail"), null),
                new Block("create", "create-edit-modal", "page.overlay", List.of("vms.create"), "CREATE"),
                new Block("update", "create-edit-modal", "page.overlay", List.of("vms.update"), "UPDATE"),
                new Block("delete", "delete-confirm-modal", "page.overlay", List.of("vms.delete"), null));
    }

    @Test
    void resourceListSkeletonWithOnlyListCapabilityProducesSingleBlock() {
        Capability list = capability("posts.list", "posts", CapabilityType.LIST);
        PageDraft page = new PageDraft("posts-page", "Posts", PageSkeletonType.RESOURCE_LIST, List.of("posts.list"));

        List<Block> blocks = resolver.resolve(page, List.of(list));

        assertThat(blocks).containsExactly(
                new Block("list", "resource-table", "page.main", List.of("posts.list"), null));
    }

    @Test
    void defaultSkeletonWithoutListCapabilityProducesNoBlocks() {
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of("vms.detail"));

        List<Block> blocks = resolver.resolve(page, List.of(detail));

        assertThat(blocks).isEmpty();
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null);
    }
}
