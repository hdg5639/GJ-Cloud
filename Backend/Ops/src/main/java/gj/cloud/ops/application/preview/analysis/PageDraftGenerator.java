package gj.cloud.ops.application.preview.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GamjaBox_2.0_Key_Features.md 3·7·8절 — API 여섯 개를 여섯 페이지로 쪼개지 않고, 같은 리소스에 속한
// capability를 하나의 페이지로 묶는다. MVP는 페이지 스켈레톤 3종만 생성한다(AUTH_PAGE/RESOURCE_LIST/
// LIST_DETAIL) — DASHBOARD는 여러 리소스에 걸친 판단이 필요해 규칙만으로 확정하지 않는다(PageSkeletonType 참고).
@Component
public class PageDraftGenerator {

    public List<PageDraft> generate(List<Capability> capabilities) {
        Map<String, List<Capability>> byResource = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            byResource.computeIfAbsent(capability.resourceName(), k -> new ArrayList<>()).add(capability);
        }

        List<PageDraft> pages = new ArrayList<>();
        for (Map.Entry<String, List<Capability>> entry : byResource.entrySet()) {
            String resourceName = entry.getKey();
            List<Capability> resourceCapabilities = entry.getValue();
            List<String> capabilityIds = resourceCapabilities.stream().map(Capability::id).toList();

            if ("auth".equals(resourceName)) {
                pages.add(new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, capabilityIds));
                continue;
            }

            boolean hasList = hasType(resourceCapabilities, CapabilityType.LIST);
            boolean hasDetail = hasType(resourceCapabilities, CapabilityType.DETAIL);
            PageSkeletonType skeleton = hasList && hasDetail ? PageSkeletonType.LIST_DETAIL : PageSkeletonType.RESOURCE_LIST;

            pages.add(new PageDraft(resourceName + "-page", titleize(resourceName), skeleton, capabilityIds));
        }
        return pages;
    }

    private boolean hasType(List<Capability> capabilities, CapabilityType type) {
        return capabilities.stream().anyMatch(c -> c.type() == type);
    }

    private String titleize(String resourceName) {
        String cleaned = resourceName.replace('-', ' ').replace('_', ' ');
        return cleaned.isBlank() ? resourceName
                : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
