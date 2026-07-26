package gj.cloud.ops.application.preview.planning.model;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.layout.LayoutBlueprints;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// WP-1 호환 Mapper. 기존 결정론적 PageDraft를 초기 PagePlan으로 승격하고, AI/사용자 Patch가
// 만든 PagePlan을 구버전 Block 경로용 PageDraft로 다시 내릴 수 있게 한다. 독립 상세 타입과 모든
// 기본 Layout reference를 보존하며, Navigation/Flow는 별도 정본 상태에서 관리한다.
public final class PagePlanMapper {

    public static List<PagePlan> from(List<PageDraft> pages, List<Capability> capabilities) {
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            capabilityById.put(capability.id(), capability);
        }
        return pages.stream().map(page -> toPagePlan(page, capabilityById)).toList();
    }

    public static List<PageDraft> toDrafts(List<PagePlan> pagePlans) {
        return pagePlans.stream().map(PagePlanMapper::toDraft).toList();
    }

    public static PageDraft toDraft(PagePlan pagePlan) {
        PageSkeletonType skeleton = switch (pagePlan.pageType()) {
            case AUTH -> PageSkeletonType.AUTH_PAGE;
            case DASHBOARD -> PageSkeletonType.DASHBOARD;
            case RESOURCE_DETAIL -> PageSkeletonType.RESOURCE_DETAIL;
            case LIST_DETAIL -> PageSkeletonType.LIST_DETAIL;
            case RESOURCE_LIST, RESOURCE_OVERVIEW, WORKFLOW, SETTINGS, ACTIVITY, FILE_MANAGER, ORGANIZATION ->
                    PageSkeletonType.RESOURCE_LIST;
        };
        return new PageDraft(pagePlan.id(), pagePlan.title(), skeleton, pagePlan.capabilityIds());
    }

    private static PagePlan toPagePlan(PageDraft page, Map<String, Capability> capabilityById) {
        boolean hasCommand = page.capabilityIds().stream()
                .map(capabilityById::get)
                .anyMatch(c -> c != null && c.kind() == CapabilityKind.COMMAND);

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("quickActions", hasCommand);
        features.put("statusSummary", false);
        features.put("childResourceTabs", false);

        return new PagePlan(
                page.id(),
                page.title(),
                // page.id() 기반 — resourceName 기반("/vms")이 아닌 이유: MERGE_PAGES 오퍼레이션 이후
                // 한 페이지가 여러 resourceName을 가질 수 있어 "리소스명 하나"가 항상 존재하지 않는다.
                "/" + page.id(),
                toPageType(page.skeleton()),
                toLayoutRef(page.skeleton()),
                page.capabilityIds(),
                List.of(),
                List.of(),
                List.of(),
                features,
                RepositoryEvidence.CONFIDENCE_HIGH,
                reasonFor(page.skeleton()),
                List.of()
        );
    }

    private static PageType toPageType(PageSkeletonType skeleton) {
        return switch (skeleton) {
            case AUTH_PAGE -> PageType.AUTH;
            case DASHBOARD -> PageType.DASHBOARD;
            case RESOURCE_LIST -> PageType.RESOURCE_LIST;
            case RESOURCE_DETAIL -> PageType.RESOURCE_DETAIL;
            case LIST_DETAIL -> PageType.LIST_DETAIL;
        };
    }

    // LayoutBlueprints.ALL의 키를 그대로 참조한다 — 오타로 어긋나면 LayoutBlueprints.ALL.get(...)이
    // null을 반환할 뿐 컴파일 시점엔 안 잡히므로, PagePlanMapperTest가 4종 전부 실제로 존재하는
    // layoutRef인지 확인한다.
    private static String toLayoutRef(PageSkeletonType skeleton) {
        String layoutId = switch (skeleton) {
            case AUTH_PAGE -> "auth-layout";
            case DASHBOARD -> "dashboard-layout";
            case RESOURCE_LIST -> "resource-list-layout";
            case RESOURCE_DETAIL -> "resource-detail-layout";
            case LIST_DETAIL -> "list-detail-layout";
        };
        return LayoutBlueprints.ALL.containsKey(layoutId) ? layoutId : null;
    }

    private static String reasonFor(PageSkeletonType skeleton) {
        return switch (skeleton) {
            case AUTH_PAGE -> "로그인 capability 기준으로 생성됨";
            case DASHBOARD -> "리소스별 LIST capability를 모아 생성됨";
            case RESOURCE_LIST -> "리소스 CRUD 규칙에 따라 생성됨(상세 없음)";
            case RESOURCE_DETAIL -> "독립 상세 페이지 계획에서 생성됨";
            case LIST_DETAIL -> "리소스 CRUD 규칙에 따라 생성됨(목록+상세)";
        };
    }

    private PagePlanMapper() {
    }
}
