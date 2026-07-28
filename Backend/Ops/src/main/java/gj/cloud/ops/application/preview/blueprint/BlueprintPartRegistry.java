package gj.cloud.ops.application.preview.blueprint;

import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// Frontend blueprints/manifests/componentManifest.ts(BLUEPRINT_PARTS)를 미러링한 Java 레지스트리.
// ComponentContracts.ALL과 같은 관례(정적 상수). BlueprintPartSelector가 이 목록에서 리소스 카테고리에
// 맞는 파츠를 골라 Block.componentId를 치환한다.
//
// **동기화 쌍**: 여기 등록된 파츠는 반드시 (1) TS 매니페스트/컴포넌트가 존재하고 (2) 포털 렌더러
// (PreviewPageRenderer + blueprints/adapters)가 그 componentId를 그릴 수 있어야 한다. 렌더러가 못 그리는
// id를 등록하면 라이브 프리뷰가 깨진다.
//
// Phase D: collection/detail/dashboard kind의 라이브러리 파츠를 category별로 확장 등록했다(모달/워크플로우
// 위저드는 렌더 kind가 달라 이 2줄 배선 대상이 아니다). 배포 런타임은 Phase B에서 preview-runtime 실물을
// 그대로 번들하므로 포털/배포가 같은 파츠를 그린다.
public final class BlueprintPartRegistry {

    // 이 파츠가 대체할 수 있는 기본 컴포넌트 계열. Selector가 Block의 (이미 compile된) 기본 componentId를
    // 보고 어떤 kind의 파츠로 갈아끼울 수 있는지 판단한다.
    public enum PartKind {
        COLLECTION,   // 목록 계열(resource-table/resource-card-grid) 대체 — slot page.main
        DETAIL,       // 전체폭 상세(full-detail-page) 대체 — slot page.main
        DASHBOARD     // 대시보드(dashboard-view/recent-activity-dashboard) 대체 — slot page.content
    }

    public record BlueprintPart(
            String componentId,
            PartKind kind,
            BlueprintCategory category,
            Set<String> acceptedSurfaces,
            // 비어 있으면 모든 purpose 허용. 특정 purpose에서만 쓰려면 명시.
            Set<Purpose> preferredPurposes
    ) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 새 파츠 배선: 여기 ALL에 한 줄 + 프론트 blueprints/adapters/registry.tsx에 한 줄.
    // (componentId 문자열이 두 곳에서 동일해야 함. 렌더/판별/union은 프론트 레지스트리에서 자동 파생.)
    // 등록 순서 = 선택 우선순위(결정론). 현재는 (kind, category) 쌍이 모두 유일하므로 순서는 무관하지만,
    // 같은 쌍에 여러 파츠를 두게 되면 앞선 것이 우선한다. kind별로 묶어 읽기 쉽게 나열한다.
    // ─────────────────────────────────────────────────────────────────────────
    public static final List<BlueprintPart> ALL = List.of(
            // ── COLLECTION (page.main) — resource-table/resource-card-grid 대체 ──
            new BlueprintPart("entity-directory", PartKind.COLLECTION, BlueprintCategory.CRM,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("kanban-collection", PartKind.COLLECTION, BlueprintCategory.PROJECT,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("commerce-product-grid", PartKind.COLLECTION, BlueprintCategory.COMMERCE,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("alert-inbox", PartKind.COLLECTION, BlueprintCategory.OBSERVABILITY,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("audit-log-table", PartKind.COLLECTION, BlueprintCategory.ADMIN,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("compact-metric-table", PartKind.COLLECTION, BlueprintCategory.ANALYTICS,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("media-gallery-collection", PartKind.COLLECTION, BlueprintCategory.CONTENT,
                    Set.of("page.main"), Set.of()),
            // ── DETAIL (page.main) — full-detail-page 대체 ──
            new BlueprintPart("infrastructure-resource-detail", PartKind.DETAIL, BlueprintCategory.INFRASTRUCTURE,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("commerce-order-detail", PartKind.DETAIL, BlueprintCategory.COMMERCE,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("content-article-detail", PartKind.DETAIL, BlueprintCategory.CONTENT,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("customer-profile-detail", PartKind.DETAIL, BlueprintCategory.CRM,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("incident-detail", PartKind.DETAIL, BlueprintCategory.OBSERVABILITY,
                    Set.of("page.main"), Set.of()),
            new BlueprintPart("settings-detail", PartKind.DETAIL, BlueprintCategory.SETTINGS,
                    Set.of("page.main"), Set.of()),
            // ── DASHBOARD (page.content) — dashboard-view/recent-activity-dashboard 대체 ──
            new BlueprintPart("operations-health-dashboard", PartKind.DASHBOARD, BlueprintCategory.OBSERVABILITY,
                    Set.of("page.content"), Set.of()),
            new BlueprintPart("admin-governance-dashboard", PartKind.DASHBOARD, BlueprintCategory.ADMIN,
                    Set.of("page.content"), Set.of()),
            new BlueprintPart("commerce-revenue-dashboard", PartKind.DASHBOARD, BlueprintCategory.COMMERCE,
                    Set.of("page.content"), Set.of()),
            new BlueprintPart("content-performance-dashboard", PartKind.DASHBOARD, BlueprintCategory.CONTENT,
                    Set.of("page.content"), Set.of()),
            new BlueprintPart("executive-kpi-dashboard", PartKind.DASHBOARD, BlueprintCategory.ANALYTICS,
                    Set.of("page.content"), Set.of()),
            new BlueprintPart("project-delivery-dashboard", PartKind.DASHBOARD, BlueprintCategory.PROJECT,
                    Set.of("page.content"), Set.of())
    );

    // 이미 compile된 기본 컴포넌트 id → 대체 가능한 파츠 kind. 대체 대상이 아니면 empty.
    public static Optional<PartKind> kindOfBaseComponent(String baseComponentId) {
        return switch (baseComponentId) {
            case "resource-table", "resource-card-grid" -> Optional.of(PartKind.COLLECTION);
            case "full-detail-page" -> Optional.of(PartKind.DETAIL);
            case "dashboard-view", "recent-activity-dashboard" -> Optional.of(PartKind.DASHBOARD);
            default -> Optional.empty();
        };
    }

    // kind·category·slot·purpose가 모두 맞는 첫 파츠(등록 순서). 없으면 empty(→ 기본 컴포넌트 유지).
    public static Optional<BlueprintPart> find(PartKind kind, BlueprintCategory category, String slot, Purpose purpose) {
        return ALL.stream()
                .filter(part -> part.kind() == kind)
                .filter(part -> part.category() == category)
                .filter(part -> part.acceptedSurfaces().contains(slot))
                .filter(part -> part.preferredPurposes().isEmpty()
                        || (purpose != null && part.preferredPurposes().contains(purpose)))
                .findFirst();
    }

    private BlueprintPartRegistry() {
    }
}
