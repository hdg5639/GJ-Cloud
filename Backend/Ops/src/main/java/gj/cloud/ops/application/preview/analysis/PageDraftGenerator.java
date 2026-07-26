package gj.cloud.ops.application.preview.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GamjaBox_2.0_Key_Features.md 3·7·8절 — API 여섯 개를 여섯 페이지로 쪼개지 않고, 같은 리소스에 속한
// capability를 하나의 페이지로 묶는다(AUTH_PAGE/RESOURCE_LIST/LIST_DETAIL). DASHBOARD는 "무엇이
// 중요한지" 같은 판단은 하지 않고, 이미 확정된 LIST capability를 리소스별 개수 카드로 기계적으로
// 나열하는 것뿐이라 AI 없이도 만들 수 있다 — 리소스가 2개 이상일 때만 생성하며, 하나뿐이면 그 리소스의
// 페이지와 내용이 겹치므로 만들지 않는다.
@Component
public class PageDraftGenerator {

    private static final int MIN_RESOURCES_FOR_DASHBOARD = 2;

    public List<PageDraft> generate(List<Capability> capabilities) {
        Map<String, List<Capability>> byResource = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            byResource.computeIfAbsent(capability.resourceName(), k -> new ArrayList<>()).add(capability);
        }

        List<PageDraft> pages = new ArrayList<>();
        List<String> listCapabilityIds = new ArrayList<>();
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
            // 스켈레톤은 page.main(RESOURCE_LIST)/page.primary(RESOURCE_DETAIL) Slot을 EXACTLY_ONE으로
            // 채울 수 있어야 유효하다. LIST 없이 RESOURCE_LIST로 만들면 목록 Block이 0개라 배포
            // compatibility 검증에서 깨진다(실제로 겪음: GET 목록이 없는 리소스). 그래서 LIST가 없고
            // DETAIL만 있으면 RESOURCE_DETAIL로 만든다.
            PageSkeletonType skeleton;
            if (hasList && hasDetail) {
                skeleton = PageSkeletonType.LIST_DETAIL;
            } else if (hasList) {
                skeleton = PageSkeletonType.RESOURCE_LIST;
            } else if (hasDetail) {
                skeleton = PageSkeletonType.RESOURCE_DETAIL;
            } else {
                // LIST도 DETAIL도 없는 리소스(예: 업로드/변환처럼 CREATE·COMMAND만 있는 엔드포인트
                // 묶음)는 page.main/page.primary를 채울 컴포넌트가 없어 MVP 페이지 형태로 표현할 수
                // 없다 — 깨진 페이지로 배포 전체를 막지 않도록 독립 페이지를 만들지 않고 건너뛴다.
                // (근본적으로는 resourceNameOf가 경로 마지막 세그먼트만 봐서 "video.create"처럼
                // 액션을 가짜 리소스로 묶는 탓이 큼 — 리소스 그룹핑 개선은 별도 작업.)
                continue;
            }

            pages.add(new PageDraft(resourceName + "-page", titleize(resourceName), skeleton, capabilityIds));

            resourceCapabilities.stream()
                    .filter(c -> c.type() == CapabilityType.LIST)
                    .map(Capability::id)
                    .forEach(listCapabilityIds::add);
        }

        if (listCapabilityIds.size() >= MIN_RESOURCES_FOR_DASHBOARD) {
            pages.add(0, new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, listCapabilityIds));
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
