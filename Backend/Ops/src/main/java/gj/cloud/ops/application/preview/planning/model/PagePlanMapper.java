package gj.cloud.ops.application.preview.planning.model;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md WP-1 — 기존 결정론적 생성기
// (RuleBasedPagePlanGenerator/PageDraftGenerator)를 건드리지 않고, 그 결과(PageDraft)에서 지금
// 확실히 알 수 있는 것만 PagePlan으로 파생한다. Navigation/LayoutBlueprint/FlowBlueprint가 채울
// 필드는 지금 빈 값으로 둔다(§22 우선순위상 아직 그 작업들이 없음).
public final class PagePlanMapper {

    public static List<PagePlan> from(List<PageDraft> pages, List<Capability> capabilities) {
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            capabilityById.put(capability.id(), capability);
        }
        return pages.stream().map(page -> toPagePlan(page, capabilityById)).toList();
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
                null,
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
            case LIST_DETAIL -> PageType.LIST_DETAIL;
        };
    }

    private static String reasonFor(PageSkeletonType skeleton) {
        return switch (skeleton) {
            case AUTH_PAGE -> "로그인 capability 기준으로 생성됨";
            case DASHBOARD -> "리소스별 LIST capability를 모아 생성됨";
            case RESOURCE_LIST -> "리소스 CRUD 규칙에 따라 생성됨(상세 없음)";
            case LIST_DETAIL -> "리소스 CRUD 규칙에 따라 생성됨(목록+상세)";
        };
    }

    private PagePlanMapper() {
    }
}
