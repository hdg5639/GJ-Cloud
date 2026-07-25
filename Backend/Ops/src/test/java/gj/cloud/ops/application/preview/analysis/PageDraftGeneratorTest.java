package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// GamjaBox_2.0_Key_Features.md 3·7절 — 같은 리소스의 capability는 한 페이지로 묶이고(6개 API를
// 6페이지로 쪼개지 않음), LIST+DETAIL을 모두 가진 리소스는 LIST_DETAIL로, LIST만 있으면 RESOURCE_LIST로 분류.
class PageDraftGeneratorTest {

    private final PageDraftGenerator generator = new PageDraftGenerator();

    @Test
    void groupsCapabilitiesByResourceAndPicksSkeletonBasedOnAvailability() {
        List<Capability> capabilities = List.of(
                capability("auth.login", "auth", CapabilityType.LOGIN),
                capability("vms.list", "vms", CapabilityType.LIST),
                capability("vms.detail", "vms", CapabilityType.DETAIL),
                capability("vms.create", "vms", CapabilityType.CREATE),
                capability("vms.delete", "vms", CapabilityType.DELETE),
                capability("tags.list", "tags", CapabilityType.LIST)
        );

        List<PageDraft> pages = generator.generate(capabilities);

        // vms/tags 두 리소스 모두 LIST capability가 있어 대시보드 페이지가 함께 생성된다.
        assertThat(pages).hasSize(4);

        PageDraft authPage = findPage(pages, "auth-login");
        assertThat(authPage.skeleton()).isEqualTo(PageSkeletonType.AUTH_PAGE);
        assertThat(authPage.capabilityIds()).containsExactly("auth.login");

        PageDraft vmsPage = findPage(pages, "vms-page");
        assertThat(vmsPage.skeleton()).isEqualTo(PageSkeletonType.LIST_DETAIL);
        assertThat(vmsPage.capabilityIds())
                .containsExactlyInAnyOrder("vms.list", "vms.detail", "vms.create", "vms.delete");

        PageDraft tagsPage = findPage(pages, "tags-page");
        assertThat(tagsPage.skeleton()).isEqualTo(PageSkeletonType.RESOURCE_LIST);

        PageDraft dashboard = findPage(pages, "dashboard");
        assertThat(dashboard.skeleton()).isEqualTo(PageSkeletonType.DASHBOARD);
        assertThat(dashboard.capabilityIds()).containsExactlyInAnyOrder("vms.list", "tags.list");
    }

    @Test
    void doesNotGenerateDashboardWithOnlyOneListCapableResource() {
        List<Capability> capabilities = List.of(
                capability("auth.login", "auth", CapabilityType.LOGIN),
                capability("vms.list", "vms", CapabilityType.LIST)
        );

        List<PageDraft> pages = generator.generate(capabilities);

        assertThat(pages).noneMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
    }

    private PageDraft findPage(List<PageDraft> pages, String id) {
        return pages.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
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
