package gj.cloud.ops.application.preview.planning.model;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Workflow Composition Phase 2 Change Request WP-1 — PagePlanMapper가 기존 PageDraft 생성기 결과에서
// 결정론적으로 알 수 있는 것만 파생하는지 확인한다(Navigation이 채울 필드는 항상 빈 값). layoutRef는
// §10(LayoutBlueprint) 조각에서 채워지게 됐다.
class PagePlanMapperTest {

    @Test
    void mapsEachSkeletonTypeToItsPageTypeAndLayoutRef() {
        List<PageDraft> pages = List.of(
                new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of()),
                new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, List.of()),
                new PageDraft("posts-page", "Posts", PageSkeletonType.RESOURCE_LIST, List.of()),
                new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL, List.of())
        );

        List<PagePlan> plans = PagePlanMapper.from(pages, List.of());

        assertThat(plans.get(0).pageType()).isEqualTo(PageType.AUTH);
        assertThat(plans.get(0).layoutRef()).isEqualTo("auth-layout");
        assertThat(plans.get(1).pageType()).isEqualTo(PageType.DASHBOARD);
        assertThat(plans.get(1).layoutRef()).isEqualTo("dashboard-layout");
        assertThat(plans.get(2).pageType()).isEqualTo(PageType.RESOURCE_LIST);
        assertThat(plans.get(2).layoutRef()).isEqualTo("resource-list-layout");
        assertThat(plans.get(3).pageType()).isEqualTo(PageType.LIST_DETAIL);
        assertThat(plans.get(3).layoutRef()).isEqualTo("list-detail-layout");
    }

    @Test
    void derivesRouteFromPageIdAndCopiesCapabilityIdsAndConfidence() {
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of("vms.list"));

        PagePlan plan = PagePlanMapper.from(List.of(page), List.of()).get(0);

        assertThat(plan.route()).isEqualTo("/vms-page");
        assertThat(plan.capabilityIds()).containsExactly("vms.list");
        assertThat(plan.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);
        assertThat(plan.layoutRef()).isEqualTo("resource-list-layout");
        assertThat(plan.routeParameters()).isEmpty();
        assertThat(plan.queryParameters()).isEmpty();
        assertThat(plan.navigationRules()).isEmpty();
        assertThat(plan.unsupportedCapabilityWarnings()).isEmpty();
    }

    @Test
    void quickActionsFeatureIsTrueOnlyWhenPageHasCommandCapability() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST, CapabilityKind.QUERY);
        Capability start = commandCapability("vms.start", "vms", "start");
        PageDraft withCommand = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST,
                List.of("vms.list", "vms.start"));
        PageDraft withoutCommand = new PageDraft("posts-page", "Posts", PageSkeletonType.RESOURCE_LIST,
                List.of("vms.list"));

        List<PagePlan> plans = PagePlanMapper.from(List.of(withCommand, withoutCommand), List.of(list, start));

        assertThat(plans.get(0).features()).containsEntry("quickActions", true);
        assertThat(plans.get(1).features()).containsEntry("quickActions", false);
    }

    private Capability capability(String id, String resourceName, CapabilityType type, CapabilityKind kind) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null, kind, null, List.of());
    }

    private Capability commandCapability(String id, String resourceName, String action) {
        return new Capability(id, resourceName, null, null, "/" + resourceName + "/{id}/" + action, "POST",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                CapabilityKind.COMMAND, action, List.of());
    }
}
