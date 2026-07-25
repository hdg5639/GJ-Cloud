package gj.cloud.ops.application.preview.planning;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.ai.PagePlanProposal;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// auto-preview-design/01-blueprint-schema.md §10 Blueprint Patch 적용 규칙과 동일한 all-or-nothing
// 원칙 — operation 하나라도 검증에 실패하면 전체를 적용하지 않고 후보 페이지 목록을 그대로 돌려준다.
// AUTH_PAGE(로그인 흐름)·DASHBOARD(LIST-only 불변식)는 이번 증분에서 대상/출처/목적지 어디로도 건드리지
// 않는다 — 의도된 안전 경계.
public final class PagePlanValidator {

    public static PagePlanApplyResult apply(List<PageDraft> candidatePages, List<Capability> capabilities,
                                             PagePlanProposal proposal) {
        Map<String, PageDraft> pagesById = new LinkedHashMap<>();
        for (PageDraft page : candidatePages) {
            pagesById.put(page.id(), page);
        }
        Set<String> validCapabilityIds = new LinkedHashSet<>();
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            validCapabilityIds.add(capability.id());
            capabilityById.put(capability.id(), capability);
        }

        List<String> errors = new ArrayList<>();
        List<String> decisions = new ArrayList<>();

        for (PagePlanOperation op : proposal.operations()) {
            switch (op.type()) {
                case RENAME_PAGE -> applyRename(op, pagesById, errors, decisions);
                case MERGE_PAGES -> applyMerge(op, pagesById, capabilityById, errors, decisions);
                case MOVE_CAPABILITY -> applyMoveCapability(op, pagesById, validCapabilityIds, errors, decisions);
            }
        }

        if (!errors.isEmpty()) {
            return new PagePlanApplyResult(candidatePages, List.of(), errors);
        }
        return new PagePlanApplyResult(List.copyOf(pagesById.values()), decisions, List.of());
    }

    private static void applyRename(PagePlanOperation op, Map<String, PageDraft> pagesById,
                                     List<String> errors, List<String> decisions) {
        PageDraft page = pagesById.get(op.pageId());
        if (page == null) {
            errors.add("RENAME_PAGE: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (isProtected(page)) {
            errors.add("RENAME_PAGE: " + page.skeleton() + " 페이지(" + op.pageId() + ")는 변경할 수 없음");
            return;
        }
        if (op.newTitle() == null || op.newTitle().isBlank()) {
            errors.add("RENAME_PAGE: 새 제목이 비어있음(" + op.pageId() + ")");
            return;
        }
        pagesById.put(page.id(), new PageDraft(page.id(), op.newTitle(), page.skeleton(), page.capabilityIds()));
        decisions.add(reasonOrDefault(op, "\"" + page.title() + "\" → \"" + op.newTitle() + "\"로 이름 변경"));
    }

    private static void applyMerge(PagePlanOperation op, Map<String, PageDraft> pagesById,
                                    Map<String, Capability> capabilityById, List<String> errors, List<String> decisions) {
        PageDraft target = pagesById.get(op.pageId());
        PageDraft other = pagesById.get(op.otherPageId());
        if (target == null || other == null) {
            errors.add("MERGE_PAGES: 존재하지 않는 pageId(" + op.pageId() + ", " + op.otherPageId() + ")");
            return;
        }
        if (op.pageId().equals(op.otherPageId())) {
            errors.add("MERGE_PAGES: 같은 페이지를 병합할 수 없음(" + op.pageId() + ")");
            return;
        }
        if (isProtected(target) || isProtected(other)) {
            errors.add("MERGE_PAGES: " + target.skeleton() + "/" + other.skeleton() + " 페이지는 병합할 수 없음");
            return;
        }

        List<String> mergedIds = new ArrayList<>(target.capabilityIds());
        for (String id : other.capabilityIds()) {
            if (!mergedIds.contains(id)) {
                mergedIds.add(id);
            }
        }
        PageSkeletonType skeleton = recomputeSkeleton(mergedIds, capabilityById);
        pagesById.put(target.id(), new PageDraft(target.id(), target.title(), skeleton, mergedIds));
        pagesById.remove(other.id());
        decisions.add(reasonOrDefault(op, "\"" + other.title() + "\" 페이지를 \"" + target.title() + "\" 페이지로 병합"));
    }

    private static void applyMoveCapability(PagePlanOperation op, Map<String, PageDraft> pagesById,
                                             Set<String> validCapabilityIds, List<String> errors, List<String> decisions) {
        if (!validCapabilityIds.contains(op.capabilityId())) {
            errors.add("MOVE_CAPABILITY: 존재하지 않는 capabilityId(" + op.capabilityId() + ")");
            return;
        }
        PageDraft destination = pagesById.get(op.destinationPageId());
        if (destination == null) {
            errors.add("MOVE_CAPABILITY: 존재하지 않는 destinationPageId(" + op.destinationPageId() + ")");
            return;
        }
        if (isProtected(destination)) {
            errors.add("MOVE_CAPABILITY: " + destination.skeleton() + " 페이지(" + op.destinationPageId() + ")로는 이동할 수 없음");
            return;
        }
        for (PageDraft page : pagesById.values()) {
            if (isProtected(page) && page.capabilityIds().contains(op.capabilityId())) {
                errors.add("MOVE_CAPABILITY: " + page.skeleton() + " 페이지(" + page.id() + ")에서는 capability를 옮길 수 없음");
                return;
            }
        }

        for (PageDraft page : List.copyOf(pagesById.values())) {
            if (page.capabilityIds().contains(op.capabilityId()) && !page.id().equals(destination.id())) {
                List<String> remaining = new ArrayList<>(page.capabilityIds());
                remaining.remove(op.capabilityId());
                pagesById.put(page.id(), new PageDraft(page.id(), page.title(), page.skeleton(), remaining));
            }
        }
        PageDraft refreshedDestination = pagesById.get(destination.id());
        if (!refreshedDestination.capabilityIds().contains(op.capabilityId())) {
            List<String> updated = new ArrayList<>(refreshedDestination.capabilityIds());
            updated.add(op.capabilityId());
            pagesById.put(destination.id(), new PageDraft(destination.id(), refreshedDestination.title(),
                    refreshedDestination.skeleton(), updated));
        }
        decisions.add(reasonOrDefault(op, op.capabilityId() + "를 \"" + destination.title() + "\" 페이지로 이동"));
    }

    // 로그인 흐름(AUTH_PAGE)과 대시보드의 LIST-only 불변식(DASHBOARD)은 이번 증분에서 건드리지 않는다.
    private static boolean isProtected(PageDraft page) {
        return page.skeleton() == PageSkeletonType.AUTH_PAGE || page.skeleton() == PageSkeletonType.DASHBOARD;
    }

    // RuleBasedPagePlanGenerator와 동일한 규칙 — LIST+DETAIL이 다 있으면 LIST_DETAIL, 아니면 RESOURCE_LIST.
    private static PageSkeletonType recomputeSkeleton(List<String> capabilityIds, Map<String, Capability> capabilityById) {
        boolean hasList = false;
        boolean hasDetail = false;
        for (String id : capabilityIds) {
            Capability capability = capabilityById.get(id);
            if (capability == null) {
                continue;
            }
            if (capability.type() == CapabilityType.LIST) {
                hasList = true;
            }
            if (capability.type() == CapabilityType.DETAIL) {
                hasDetail = true;
            }
        }
        return hasList && hasDetail ? PageSkeletonType.LIST_DETAIL : PageSkeletonType.RESOURCE_LIST;
    }

    private static String reasonOrDefault(PagePlanOperation op, String fallback) {
        return op.reason() != null && !op.reason().isBlank() ? op.reason() : fallback;
    }

    private PagePlanValidator() {
    }
}
