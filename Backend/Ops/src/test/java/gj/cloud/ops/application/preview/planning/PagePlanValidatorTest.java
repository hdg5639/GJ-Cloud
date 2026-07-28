package gj.cloud.ops.application.preview.planning;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.ai.PagePlanOperationType;
import gj.cloud.ops.application.preview.ai.PagePlanProposal;
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

class PagePlanValidatorTest {

    @Test
    void renamesPageAndKeepsOtherFieldsUnchanged() {
        Capability list = listCapability("vms");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.RENAME_PAGE, "vms-page", null, "내 가상머신", null, null, "도메인 이름 반영")));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(page), List.of(list), proposal);

        assertThat(result.errors()).isEmpty();
        PageDraft renamed = result.pages().get(0);
        assertThat(renamed.title()).isEqualTo("내 가상머신");
        assertThat(renamed.skeleton()).isEqualTo(PageSkeletonType.RESOURCE_LIST);
        assertThat(renamed.capabilityIds()).containsExactly(list.id());
        assertThat(result.decisions()).containsExactly("도메인 이름 반영");
    }

    @Test
    void mergesTwoPagesAndRecomputesSkeletonToListDetail() {
        Capability list = listCapability("vms");
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        Capability tagsList = listCapability("tags");
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PageDraft detailPage = new PageDraft("vms-detail-page", "Vms Detail", PageSkeletonType.RESOURCE_LIST, List.of(detail.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MERGE_PAGES, "vms-page", "vms-detail-page", null, null, null, "하나의 흐름")));

        PagePlanApplyResult result = PagePlanValidator.apply(
                List.of(vmsPage, detailPage), List.of(list, detail, tagsList), proposal);

        assertThat(result.errors()).isEmpty();
        assertThat(result.pages()).extracting(PageDraft::id).containsExactly("vms-page");
        PageDraft merged = result.pages().get(0);
        assertThat(merged.skeleton()).isEqualTo(PageSkeletonType.LIST_DETAIL);
        assertThat(merged.capabilityIds()).containsExactlyInAnyOrder(list.id(), detail.id());
    }

    @Test
    void movesCapabilityBetweenPagesWithoutDuplication() {
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        Capability tagsList = listCapability("tags");
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(create.id()));
        PageDraft tagsPage = new PageDraft("tags-page", "Tags", PageSkeletonType.RESOURCE_LIST, List.of(tagsList.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, create.id(), "tags-page", "함께 관리")));

        PagePlanApplyResult result = PagePlanValidator.apply(
                List.of(vmsPage, tagsPage), List.of(create, tagsList), proposal);

        assertThat(result.errors()).isEmpty();
        PageDraft resultVmsPage = findPage(result.pages(), "vms-page");
        PageDraft resultTagsPage = findPage(result.pages(), "tags-page");
        assertThat(resultVmsPage.capabilityIds()).doesNotContain(create.id());
        assertThat(resultTagsPage.capabilityIds()).containsExactlyInAnyOrder(tagsList.id(), create.id());
    }

    @Test
    void rejectsOperationReferencingUnknownPageId() {
        Capability list = listCapability("vms");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.RENAME_PAGE, "does-not-exist", null, "새 이름", null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(page), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
        assertThat(result.pages()).containsExactly(page);
    }

    @Test
    void rejectsOperationReferencingUnknownCapabilityId() {
        Capability list = listCapability("vms");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, "ghost.capability", "vms-page", null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(page), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void rejectsRenamingAuthPage() {
        Capability login = capability("auth.login", "auth", CapabilityType.LOGIN);
        PageDraft authPage = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of(login.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.RENAME_PAGE, "auth-login", null, "입장하기", null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(authPage), List.of(login), proposal);

        assertThat(result.errors()).isNotEmpty();
        assertThat(result.pages()).containsExactly(authPage);
    }

    @Test
    void rejectsMovingCapabilityIntoDashboard() {
        Capability list = listCapability("vms");
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id(), create.id()));
        PageDraft dashboard = new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, create.id(), "dashboard", null)));

        PagePlanApplyResult result = PagePlanValidator.apply(
                List.of(vmsPage, dashboard), List.of(list, create), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void rejectsMovingCapabilityOutOfDashboard() {
        Capability list = listCapability("vms");
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PageDraft dashboard = new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, list.id(), "vms-page", null)));

        PagePlanApplyResult result = PagePlanValidator.apply(
                List.of(vmsPage, dashboard), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void wholeProposalIsRejectedWhenAnyOperationFails() {
        Capability list = listCapability("vms");
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.RENAME_PAGE, "vms-page", null, "좋은 이름", null, null, null),
                new PagePlanOperation(PagePlanOperationType.RENAME_PAGE, "does-not-exist", null, "다른 이름", null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(page), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
        // all-or-nothing — 첫 번째 operation이 유효했더라도 전체가 반영되지 않아야 한다.
        assertThat(result.pages().get(0).title()).isEqualTo("Vms");
    }

    @Test
    void addsPageThenMovesCapabilityOntoItInTheSameProposal() {
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(create.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.ADD_PAGE, "settings-page", null, "설정", null, null, "분리"),
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, create.id(), "settings-page", null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(vmsPage), List.of(create), proposal);

        assertThat(result.errors()).isEmpty();
        PageDraft settingsPage = findPage(result.pages(), "settings-page");
        assertThat(settingsPage.title()).isEqualTo("설정");
        assertThat(settingsPage.capabilityIds()).containsExactly(create.id());
        assertThat(findPage(result.pages(), "vms-page").capabilityIds()).doesNotContain(create.id());
    }

    @Test
    void rejectsAddPageWithAlreadyUsedId() {
        Capability list = listCapability("vms");
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.ADD_PAGE, "vms-page", null, "중복", null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(vmsPage), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void removesEmptyPageAfterMovingItsOnlyCapabilityAway() {
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        Capability list = listCapability("vms");
        PageDraft settingsPage = new PageDraft("settings-page", "설정", PageSkeletonType.RESOURCE_LIST, List.of(create.id()));
        PageDraft vmsPage = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(list.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.MOVE_CAPABILITY, null, null, null, create.id(), "vms-page", null),
                new PagePlanOperation(PagePlanOperationType.REMOVE_PAGE, "settings-page", null, null, null, null, "빈 페이지 정리")));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(settingsPage, vmsPage), List.of(create, list), proposal);

        assertThat(result.errors()).isEmpty();
        assertThat(result.pages()).extracting(PageDraft::id).containsExactly("vms-page");
    }

    @Test
    void rejectsRemovingPageThatStillHasCapabilities() {
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of(create.id()));
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.REMOVE_PAGE, "vms-page", null, null, null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(page), List.of(create), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void rejectsRemovingDashboard() {
        Capability list = listCapability("vms");
        PageDraft dashboard = new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, List.of());
        PagePlanProposal proposal = new PagePlanProposal(List.of(
                new PagePlanOperation(PagePlanOperationType.REMOVE_PAGE, "dashboard", null, null, null, null, null)));

        PagePlanApplyResult result = PagePlanValidator.apply(List.of(dashboard), List.of(list), proposal);

        assertThat(result.errors()).isNotEmpty();
    }

    private PageDraft findPage(List<PageDraft> pages, String id) {
        return pages.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private Capability listCapability(String resourceName) {
        return capability(resourceName + ".list", resourceName, CapabilityType.LIST);
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
