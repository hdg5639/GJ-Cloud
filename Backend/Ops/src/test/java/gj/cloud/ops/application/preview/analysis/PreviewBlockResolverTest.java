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

        assertThat(blocks).startsWith(
                new Block("dashboard", "dashboard-view", "page.content", List.of("vms.list", "tags.list"), null));
        assertPageChrome(blocks, "vms.list");
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

        assertThat(blocks).startsWith(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                new Block("detail", "detail-panel", "page.aside", List.of("vms.detail"), null),
                new Block("create", "create-edit-modal", "page.overlay", List.of("vms.create"), "CREATE"),
                new Block("update", "create-edit-modal", "page.overlay", List.of("vms.update"), "UPDATE"),
                new Block("delete", "delete-confirm-modal", "page.overlay", List.of("vms.delete"), "DELETE"));
        assertPageChrome(blocks, "vms.list");
    }

    @Test
    void resourceDetailSkeletonProducesFullDetailActionsAndChildResourcesWithoutListBlock() {
        Capability detail = capabilityWithPath("vms.detail", "vms", CapabilityType.DETAIL,
                "/vms/{vmId}", "GET");
        Capability start = commandCapability("vms.start", "vms", "start");
        Capability portsList = capabilityWithPath("ports.list", "ports", CapabilityType.LIST,
                "/vms/{vmId}/ports", "GET");
        Capability portsCreate = capabilityWithPath("ports.create", "ports", CapabilityType.CREATE,
                "/vms/{vmId}/ports", "POST");
        PageDraft page = new PageDraft("vm-detail", "VM 상세", PageSkeletonType.RESOURCE_DETAIL,
                List.of(detail.id(), start.id(), portsList.id(), portsCreate.id()));

        List<Block> blocks = resolver.resolve(page, List.of(detail, start, portsList, portsCreate));

        assertThat(blocks).startsWith(
                // 독립 상세 페이지는 page.primary 슬롯에 full-detail-page를 쓴다 — detail-panel은
                // ComponentContracts상 page.aside 전용이라 page.primary에 놓으면 슬롯 계약 위반이다.
                new Block("detail", "full-detail-page", "page.primary", List.of(detail.id()), null),
                new Block("actions", "quick-action-button-group", "page.actions", List.of(start.id()), "COMMAND"),
                new Block("child-ports", "child-resource-list", "page.secondary",
                        List.of(portsList.id(), portsCreate.id()), null));
        assertPageChrome(blocks, detail.id());
        assertThat(blocks).noneMatch(block -> block.componentId().equals("resource-table"));
    }

    @Test
    void resourceListSkeletonWithOnlyListCapabilityProducesSingleBlock() {
        Capability list = capability("posts.list", "posts", CapabilityType.LIST);
        PageDraft page = new PageDraft("posts-page", "Posts", PageSkeletonType.RESOURCE_LIST, List.of("posts.list"));

        List<Block> blocks = resolver.resolve(page, List.of(list));

        assertThat(blocks).startsWith(
                new Block("list", "resource-table", "page.main", List.of("posts.list"), null));
        assertPageChrome(blocks, "posts.list");
    }

    @Test
    void defaultSkeletonWithoutListCapabilityProducesNoBlocks() {
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of("vms.detail"));

        List<Block> blocks = resolver.resolve(page, List.of(detail));

        assertThat(blocks).isEmpty();
    }

    @Test
    void defaultSkeletonWithCommandCapabilityAddsActionButtonGroupBlock() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        Capability start = commandCapability("vms.start", "vms", "start");
        Capability stop = commandCapability("vms.stop", "vms", "stop");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST,
                List.of("vms.list", "vms.start", "vms.stop"));

        List<Block> blocks = resolver.resolve(page, List.of(list, start, stop));

        assertThat(blocks).startsWith(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null),
                new Block("actions", "quick-action-button-group", "page.actions",
                        List.of("vms.start", "vms.stop"), "COMMAND"));
        assertPageChrome(blocks, "vms.list");
    }

    @Test
    void defaultSkeletonWithoutCommandCapabilityProducesNoActionsBlock() {
        Capability list = capability("posts.list", "posts", CapabilityType.LIST);
        PageDraft page = new PageDraft("posts-page", "Posts", PageSkeletonType.RESOURCE_LIST, List.of("posts.list"));

        List<Block> blocks = resolver.resolve(page, List.of(list));

        assertThat(blocks).noneMatch(b -> b.componentId().equals("quick-action-button-group"));
    }

    @Test
    void nestedListAndCreateCapabilitiesProduceChildResourceBlock() {
        Capability vmsList = capabilityWithPath("vms.list", "vms", CapabilityType.LIST, "/vms", "GET");
        Capability vmsDetail = capabilityWithPath("vms.detail", "vms", CapabilityType.DETAIL, "/vms/{vmId}", "GET");
        Capability portsList = capabilityWithPath("ports.list", "ports", CapabilityType.LIST,
                "/vms/{vmId}/ports", "GET");
        Capability portsCreate = capabilityWithPath("ports.create", "ports", CapabilityType.CREATE,
                "/vms/{vmId}/ports", "POST");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                List.of("vms.list", "vms.detail", "ports.list", "ports.create"));

        List<Block> blocks = resolver.resolve(page, List.of(vmsList, vmsDetail, portsList, portsCreate));

        assertThat(blocks).contains(new Block("child-ports", "child-resource-list", "page.secondary",
                List.of("ports.list", "ports.create"), null));
    }

    @Test
    void unrelatedMergedListIsNotTreatedAsChildResource() {
        Capability vmsList = capabilityWithPath("vms.list", "vms", CapabilityType.LIST, "/vms", "GET");
        Capability tagsList = capabilityWithPath("tags.list", "tags", CapabilityType.LIST, "/tags", "GET");
        PageDraft page = new PageDraft("merged-page", "Merged", PageSkeletonType.RESOURCE_LIST,
                List.of("vms.list", "tags.list"));

        List<Block> blocks = resolver.resolve(page, List.of(vmsList, tagsList));

        assertThat(blocks).noneMatch(block -> block.componentId().equals("child-resource-list"));
    }

    private void assertPageChrome(List<Block> blocks, String capabilityId) {
        assertThat(blocks).endsWith(
                new Block("layout", "default-layout", "page.layout", List.of(capabilityId), null),
                new Block("navigation", "default-navigation", "page.navigation", List.of(capabilityId), null),
                new Block("feedback", "default-feedback", "page.feedback", List.of(capabilityId), null),
                new Block("theme", "default-theme", "page.theme", List.of(capabilityId), null));
    }

    private Capability capabilityWithPath(String id, String resourceName, CapabilityType type, String path, String method) {
        return new Capability(id, resourceName, type, null, path, method,
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                type == CapabilityType.CREATE ? RiskLevel.STATE_CHANGING : RiskLevel.SAFE,
                type == CapabilityType.CREATE ? AutomationPolicy.USER_INITIATED : AutomationPolicy.AUTO_SAFE,
                null, null, kindOf(type), null, List.of());
    }

    private Capability commandCapability(String id, String resourceName, String action) {
        return new Capability(id, resourceName, null, null, "/" + resourceName + "/{id}/" + action, "POST",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                CapabilityKind.COMMAND, action, List.of());
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                kindOf(type), null, List.of());
    }

    private CapabilityKind kindOf(CapabilityType type) {
        return switch (type) {
            case LIST, DETAIL -> CapabilityKind.QUERY;
            case CREATE, UPDATE, DELETE -> CapabilityKind.MUTATION;
            case LOGIN -> CapabilityKind.AUTH;
        };
    }
}
