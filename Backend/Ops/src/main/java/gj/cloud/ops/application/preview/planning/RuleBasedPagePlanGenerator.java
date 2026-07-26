package gj.cloud.ops.application.preview.planning;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md Increment 2 — PageDraftGenerator와 같은
// 리소스 그룹핑 규칙을 쓰되(그 클래스는 건드리지 않고 FALLBACK_CRUD 전용으로 남겨둠), purpose가 실제로
// 페이지 구성에 영향을 주게 한다. 첫 증분이라 AI 없이 규칙만으로 판단 가능한 축(대시보드 포함 여부)만
// 다룬다 — 서비스 설명을 해석한 도메인 인지형 페이지 재구성(§19 예시)은 Increment 3(AiPagePlanner)의 몫.
@Component
public class RuleBasedPagePlanGenerator {

    public List<PageDraft> generate(List<Capability> capabilities, Purpose purpose) {
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
            // compatibility 검증에서 깨진다(실제로 겪음: GET 목록이 없는 리소스). LIST가 없고 DETAIL만
            // 있으면 RESOURCE_DETAIL로, 둘 다 없으면(업로드/변환 등 CREATE·COMMAND만) 유효한 페이지를
            // 만들 수 없어 건너뛴다. (PageDraftGenerator와 동일 규칙 — 두 생성기가 같은 계약을 지켜야 함.)
            PageSkeletonType skeleton;
            if (hasList && hasDetail) {
                skeleton = PageSkeletonType.LIST_DETAIL;
            } else if (hasList) {
                skeleton = PageSkeletonType.RESOURCE_LIST;
            } else if (hasDetail) {
                skeleton = PageSkeletonType.RESOURCE_DETAIL;
            } else {
                continue;
            }

            pages.add(new PageDraft(resourceName + "-page", titleize(resourceName), skeleton, capabilityIds));

            if (hasList) {
                resourceCapabilities.stream()
                        .filter(c -> c.type() == CapabilityType.LIST)
                        .map(Capability::id)
                        .forEach(listCapabilityIds::add);
            }
        }

        if (shouldIncludeDashboard(listCapabilityIds.size(), purpose)) {
            pages.add(0, new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, listCapabilityIds));
        }
        return pages;
    }

    // Change Request §3(생성 목적별 UI 특징) 축소판 — ADMIN은 "Operational summaries"를 요구하므로
    // 리소스가 하나뿐이어도 대시보드를 보여준다. API_TEST는 "Minimal navigation"을 요구하므로 아예 안
    // 만든다. purpose가 없거나 PRODUCT_LIKE는 기존 기준(리소스 2개 이상)을 유지한다.
    private boolean shouldIncludeDashboard(int listCapabilityCount, Purpose purpose) {
        if (purpose == Purpose.API_TEST) {
            return false;
        }
        if (purpose == Purpose.ADMIN) {
            return listCapabilityCount >= 1;
        }
        return listCapabilityCount >= 2;
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
