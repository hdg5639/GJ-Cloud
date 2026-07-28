package gj.cloud.ops.application.preview.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// auto-preview-design/01-blueprint-schema.md — PageDraft(스켈레톤 타입)를 렌더러가 그대로 순회할 수
// 있는 Block 목록으로 변환한다. PreviewPageRenderer.tsx/PreviewComposeArtifactBuilder의 TS 템플릿에
// 하드코딩돼 있던 규칙을 그대로 옮긴 것 — 동작은 바꾸지 않는다. RESOURCE_LIST와 LIST_DETAIL은 렌더링
// 규칙이 동일해(PageDraftGenerator가 타이틀 고를 때만 구분) 같은 분기로 처리한다.
@Component
public class PreviewBlockResolver {

    public Map<String, List<Block>> resolveAll(List<PageDraft> pages, List<Capability> capabilities) {
        Map<String, List<Block>> result = new LinkedHashMap<>();
        for (PageDraft page : pages) {
            result.put(page.id(), resolve(page, capabilities));
        }
        return result;
    }

    public List<Block> resolve(PageDraft page, List<Capability> capabilities) {
        List<Block> blocks = switch (page.skeleton()) {
            case AUTH_PAGE -> resolveAuthPage(page, capabilities);
            case DASHBOARD -> resolveDashboard(page, capabilities);
            case RESOURCE_DETAIL -> resolveResourceDetail(page, capabilities);
            case RESOURCE_LIST, LIST_DETAIL -> resolveDefault(page, capabilities);
        };
        if (page.skeleton() == PageSkeletonType.AUTH_PAGE || blocks.isEmpty()) {
            return blocks;
        }
        return withPageChrome(page, blocks);
    }

    // 레이아웃/내비게이션/피드백/테마는 데이터 Block을 대체하지 않고 페이지를 감싸는 독립 mount다.
    // 기본 synthetic Block을 항상 넣어두면 Selector가 같은 manifest에서 자동 선택하고, 마법사도
    // pageId/instanceId override라는 기존 메커니즘 그대로 4개 축을 선택할 수 있다.
    private List<Block> withPageChrome(PageDraft page, List<Block> blocks) {
        String primaryCapabilityId = page.capabilityIds().stream().findFirst().orElse(null);
        List<String> capabilityIds = primaryCapabilityId == null ? List.of() : List.of(primaryCapabilityId);
        List<Block> result = new ArrayList<>(blocks);
        result.add(new Block("layout", "default-layout", "page.layout", capabilityIds, null));
        result.add(new Block("navigation", "default-navigation", "page.navigation", capabilityIds, null));
        result.add(new Block("feedback", "default-feedback", "page.feedback", capabilityIds, null));
        result.add(new Block("theme", "default-theme", "page.theme", capabilityIds, null));
        return List.copyOf(result);
    }

    private List<Block> resolveAuthPage(PageDraft page, List<Capability> capabilities) {
        Capability login = findByType(page, capabilities, CapabilityType.LOGIN);
        if (login == null) {
            return List.of();
        }
        return List.of(new Block("login", "login-form", "page.content", List.of(login.id()), null));
    }

    private List<Block> resolveDashboard(PageDraft page, List<Capability> capabilities) {
        List<String> listCapabilityIds = page.capabilityIds().stream()
                .map(id -> findById(capabilities, id))
                .filter(c -> c != null && c.type() == CapabilityType.LIST)
                .map(Capability::id)
                .toList();
        return List.of(new Block("dashboard", "dashboard-view", "page.content", listCapabilityIds, null));
    }

    private List<Block> resolveResourceDetail(PageDraft page, List<Capability> capabilities) {
        Capability detail = findByType(page, capabilities, CapabilityType.DETAIL);
        if (detail == null) {
            return List.of();
        }
        Capability update = findByTypeForResource(page, capabilities, CapabilityType.UPDATE, detail.resourceName());
        Capability delete = findByTypeForResource(page, capabilities, CapabilityType.DELETE, detail.resourceName());

        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("detail", "full-detail-page", "page.primary", List.of(detail.id()), null));

        List<String> commandIds = page.capabilityIds().stream()
                .map(id -> findById(capabilities, id))
                .filter(c -> c != null && c.kind() == CapabilityKind.COMMAND)
                .map(Capability::id)
                .toList();
        if (!commandIds.isEmpty()) {
            blocks.add(new Block("actions", "quick-action-button-group", "page.actions", commandIds, "COMMAND"));
        }
        if (update != null) {
            blocks.add(new Block("update", "create-edit-modal", "page.overlay", List.of(update.id()), "UPDATE"));
        }
        if (delete != null) {
            blocks.add(new Block("delete", "delete-confirm-modal", "page.overlay", List.of(delete.id()), "DELETE"));
        }

        for (Capability childList : page.capabilityIds().stream()
                .map(id -> findById(capabilities, id))
                .filter(c -> c != null && c.type() == CapabilityType.LIST)
                .filter(c -> isNestedUnderDetail(detail, c))
                .toList()) {
            List<String> childCapabilityIds = new ArrayList<>();
            childCapabilityIds.add(childList.id());
            addChildAction(childCapabilityIds, page, capabilities, childList.resourceName(),
                    c -> isNestedUnderDetail(detail, c));
            blocks.add(new Block("child-" + sanitizeInstanceId(childList.resourceName()),
                    "child-resource-list", "page.secondary", childCapabilityIds, null));
        }
        return blocks;
    }

    // 자식 리소스 블록에 CREATE/UPDATE/DELETE capability를 함께 담는다 — 담아두면 child-resource-list가
    // 행별 추가/수정/삭제 버튼을 그린다(없으면 목록만). 부모 경로 아래로 중첩된 것만 고른다.
    private void addChildAction(List<String> childCapabilityIds, PageDraft page, List<Capability> capabilities,
                                String childResourceName, java.util.function.Predicate<Capability> nested) {
        for (CapabilityType type : List.of(CapabilityType.CREATE, CapabilityType.UPDATE, CapabilityType.DELETE)) {
            page.capabilityIds().stream()
                    .map(id -> findById(capabilities, id))
                    .filter(c -> c != null && c.type() == type)
                    .filter(c -> c.resourceName().equals(childResourceName))
                    .filter(nested)
                    .findFirst()
                    .ifPresent(c -> childCapabilityIds.add(c.id()));
        }
    }

    private boolean isNestedUnderDetail(Capability detail, Capability candidate) {
        String detailPath = detail.path();
        int lastParamStart = detailPath.lastIndexOf("/{");
        if (lastParamStart < 0) {
            return false;
        }
        String prefix = detailPath.substring(0, lastParamStart);
        return candidate.path().startsWith(prefix + "/{") && !candidate.path().equals(detailPath);
    }

    private List<Block> resolveDefault(PageDraft page, List<Capability> capabilities) {
        List<Capability> listCapabilities = page.capabilityIds().stream()
                .map(id -> findById(capabilities, id))
                .filter(c -> c != null && c.type() == CapabilityType.LIST)
                .toList();
        if (listCapabilities.isEmpty()) {
            return List.of();
        }
        Capability list = listCapabilities.get(0);
        Capability detail = findByTypeForResource(page, capabilities, CapabilityType.DETAIL, list.resourceName());
        Capability create = findByTypeForResource(page, capabilities, CapabilityType.CREATE, list.resourceName());
        Capability update = findByTypeForResource(page, capabilities, CapabilityType.UPDATE, list.resourceName());
        Capability delete = findByTypeForResource(page, capabilities, CapabilityType.DELETE, list.resourceName());

        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("list", "resource-table", "page.main", List.of(list.id()), null));
        if (detail != null) {
            blocks.add(new Block("detail", "detail-panel", "page.aside", List.of(detail.id()), null));
        }
        if (create != null) {
            blocks.add(new Block("create", "create-edit-modal", "page.overlay", List.of(create.id()), "CREATE"));
        }
        if (update != null) {
            blocks.add(new Block("update", "create-edit-modal", "page.overlay", List.of(update.id()), "UPDATE"));
        }
        if (delete != null) {
            blocks.add(new Block("delete", "delete-confirm-modal", "page.overlay", List.of(delete.id()), "DELETE"));
        }

        // Direction Recovery Change Request §22 완료 기준 — COMMAND capability(vm.start 등)를 discard하지
        // 않고 page.actions Block으로 노출한다. 리소스 하나에 여러 개(start/stop/restart) 있을 수 있어
        // dashboard-view처럼 한 Block에 전부 담는다.
        List<String> commandIds = page.capabilityIds().stream()
                .map(id -> findById(capabilities, id))
                .filter(c -> c != null && c.kind() == CapabilityKind.COMMAND)
                .map(Capability::id)
                .toList();
        if (!commandIds.isEmpty()) {
            blocks.add(new Block("actions", "quick-action-button-group", "page.actions", commandIds, "COMMAND"));
        }

        // AC-6 — 주 LIST 경로 아래에 중첩된 추가 LIST(/parents/{id}/children)가 같은 페이지에
        // 배치돼 있으면 부모 상세의 secondary section으로 조립한다. 단순 MERGE_PAGES로 합쳐진
        // 무관한 리소스는 경로 prefix가 맞지 않아 child로 오인하지 않는다.
        for (Capability childList : listCapabilities.stream().skip(1).toList()) {
            if (!isNestedUnder(list, childList)) {
                continue;
            }
            List<String> childCapabilityIds = new ArrayList<>();
            childCapabilityIds.add(childList.id());
            addChildAction(childCapabilityIds, page, capabilities, childList.resourceName(),
                    c -> isNestedUnder(list, c));
            blocks.add(new Block("child-" + sanitizeInstanceId(childList.resourceName()),
                    "child-resource-list", "page.secondary", childCapabilityIds, null));
        }
        return blocks;
    }


    private boolean isNestedUnder(Capability parentList, Capability candidate) {
        String parentPath = parentList.path().endsWith("/")
                ? parentList.path().substring(0, parentList.path().length() - 1)
                : parentList.path();
        return candidate.path().startsWith(parentPath + "/{");
    }

    private String sanitizeInstanceId(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // PreviewPageRenderer.tsx의 findCapabilityByType과 동일한 규칙 — page.capabilityIds 순서대로
    // 훑어 첫 번째로 타입이 일치하는 capability를 쓴다.
    private Capability findByType(PageDraft page, List<Capability> capabilities, CapabilityType type) {
        for (String id : page.capabilityIds()) {
            Capability capability = findById(capabilities, id);
            if (capability != null && capability.type() == type) {
                return capability;
            }
        }
        return null;
    }

    private Capability findByTypeForResource(PageDraft page, List<Capability> capabilities,
                                                     CapabilityType type, String resourceName) {
        for (String id : page.capabilityIds()) {
            Capability capability = findById(capabilities, id);
            if (capability != null && capability.type() == type && capability.resourceName().equals(resourceName)) {
                return capability;
            }
        }
        return null;
    }

    private Capability findById(List<Capability> capabilities, String id) {
        return capabilities.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
