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
        return switch (page.skeleton()) {
            case AUTH_PAGE -> resolveAuthPage(page, capabilities);
            case DASHBOARD -> resolveDashboard(page, capabilities);
            case RESOURCE_LIST, LIST_DETAIL -> resolveDefault(page, capabilities);
        };
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

    private List<Block> resolveDefault(PageDraft page, List<Capability> capabilities) {
        Capability list = findByType(page, capabilities, CapabilityType.LIST);
        if (list == null) {
            return List.of();
        }
        Capability detail = findByType(page, capabilities, CapabilityType.DETAIL);
        Capability create = findByType(page, capabilities, CapabilityType.CREATE);
        Capability update = findByType(page, capabilities, CapabilityType.UPDATE);
        Capability delete = findByType(page, capabilities, CapabilityType.DELETE);

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
            blocks.add(new Block("delete", "delete-confirm-modal", "page.overlay", List.of(delete.id()), null));
        }
        return blocks;
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

    private Capability findById(List<Capability> capabilities, String id) {
        return capabilities.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
