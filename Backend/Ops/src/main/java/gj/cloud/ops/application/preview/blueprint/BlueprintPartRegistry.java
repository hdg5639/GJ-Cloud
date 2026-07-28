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
                    Set.of("page.content"), Set.of()),
            // ── Expansion Pack COLLECTION ──
            new BlueprintPart("threat-event-stream", PartKind.COLLECTION, BlueprintCategory.SECURITY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("vulnerability-matrix", PartKind.COLLECTION, BlueprintCategory.SECURITY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("support-ticket-inbox", PartKind.COLLECTION, BlueprintCategory.SUPPORT,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("customer-health-board", PartKind.COLLECTION, BlueprintCategory.CRM,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("transaction-ledger", PartKind.COLLECTION, BlueprintCategory.FINANCE,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("invoice-collection", PartKind.COLLECTION, BlueprintCategory.BILLING,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("inventory-sku-matrix", PartKind.COLLECTION, BlueprintCategory.INVENTORY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("warehouse-bin-explorer", PartKind.COLLECTION, BlueprintCategory.INVENTORY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("shipment-tracking-board", PartKind.COLLECTION, BlueprintCategory.LOGISTICS,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("route-stop-timeline", PartKind.COLLECTION, BlueprintCategory.LOGISTICS,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("reservation-calendar", PartKind.COLLECTION, BlueprintCategory.BOOKING,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("venue-seat-map", PartKind.COLLECTION, BlueprintCategory.EVENTS,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("course-catalog-grid", PartKind.COLLECTION, BlueprintCategory.EDUCATION,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("learner-roster", PartKind.COLLECTION, BlueprintCategory.EDUCATION,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("employee-directory-grid", PartKind.COLLECTION, BlueprintCategory.HR,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("candidate-pipeline", PartKind.COLLECTION, BlueprintCategory.HR,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("api-endpoint-catalog", PartKind.COLLECTION, BlueprintCategory.DEVELOPER,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("deployment-environment-matrix", PartKind.COLLECTION, BlueprintCategory.DEVELOPER,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("model-registry-collection", PartKind.COLLECTION, BlueprintCategory.AI,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("prompt-library-grid", PartKind.COLLECTION, BlueprintCategory.AI,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("device-topology-list", PartKind.COLLECTION, BlueprintCategory.IOT,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("sensor-reading-table", PartKind.COLLECTION, BlueprintCategory.IOT,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("property-listing-grid", PartKind.COLLECTION, BlueprintCategory.REAL_ESTATE,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("tenant-directory", PartKind.COLLECTION, BlueprintCategory.REAL_ESTATE,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("community-feed", PartKind.COLLECTION, BlueprintCategory.COMMUNITY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("moderation-queue", PartKind.COLLECTION, BlueprintCategory.COMMUNITY,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("vendor-marketplace-grid", PartKind.COLLECTION, BlueprintCategory.MARKETPLACE,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("trip-itinerary-collection", PartKind.COLLECTION, BlueprintCategory.TRAVEL,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("legal-case-docket", PartKind.COLLECTION, BlueprintCategory.LEGAL,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("asset-production-board", PartKind.COLLECTION, BlueprintCategory.MEDIA,
                    Set.of("page.main", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            // ── Expansion Pack DETAIL ──
            new BlueprintPart("threat-incident-detail", PartKind.DETAIL, BlueprintCategory.SECURITY,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("vulnerability-detail", PartKind.DETAIL, BlueprintCategory.SECURITY,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("support-ticket-detail", PartKind.DETAIL, BlueprintCategory.SUPPORT,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("customer-success-detail", PartKind.DETAIL, BlueprintCategory.CRM,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("transaction-detail", PartKind.DETAIL, BlueprintCategory.FINANCE,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("invoice-detail", PartKind.DETAIL, BlueprintCategory.BILLING,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("inventory-item-detail", PartKind.DETAIL, BlueprintCategory.INVENTORY,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("shipment-detail", PartKind.DETAIL, BlueprintCategory.LOGISTICS,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("reservation-detail", PartKind.DETAIL, BlueprintCategory.BOOKING,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("event-detail", PartKind.DETAIL, BlueprintCategory.EVENTS,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("course-detail", PartKind.DETAIL, BlueprintCategory.EDUCATION,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("learner-detail", PartKind.DETAIL, BlueprintCategory.EDUCATION,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("employee-profile-detail", PartKind.DETAIL, BlueprintCategory.HR,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("candidate-detail", PartKind.DETAIL, BlueprintCategory.HR,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("api-product-detail", PartKind.DETAIL, BlueprintCategory.DEVELOPER,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("deployment-detail", PartKind.DETAIL, BlueprintCategory.DEVELOPER,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("model-detail", PartKind.DETAIL, BlueprintCategory.AI,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("prompt-detail", PartKind.DETAIL, BlueprintCategory.AI,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("device-detail", PartKind.DETAIL, BlueprintCategory.IOT,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("property-detail", PartKind.DETAIL, BlueprintCategory.REAL_ESTATE,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("community-member-detail", PartKind.DETAIL, BlueprintCategory.COMMUNITY,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("vendor-detail", PartKind.DETAIL, BlueprintCategory.MARKETPLACE,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("trip-detail", PartKind.DETAIL, BlueprintCategory.TRAVEL,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("legal-matter-detail", PartKind.DETAIL, BlueprintCategory.LEGAL,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("media-asset-detail", PartKind.DETAIL, BlueprintCategory.MEDIA,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("knowledge-article-detail", PartKind.DETAIL, BlueprintCategory.KNOWLEDGE,
                    Set.of("page.main", "page.aside", "page.content"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            // ── Expansion Pack DASHBOARD ──
            new BlueprintPart("security-threat-dashboard", PartKind.DASHBOARD, BlueprintCategory.SECURITY,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("soc-overview-dashboard", PartKind.DASHBOARD, BlueprintCategory.SECURITY,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("support-sla-dashboard", PartKind.DASHBOARD, BlueprintCategory.SUPPORT,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("customer-success-dashboard", PartKind.DASHBOARD, BlueprintCategory.CRM,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("finance-cashflow-dashboard", PartKind.DASHBOARD, BlueprintCategory.FINANCE,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("billing-revenue-dashboard", PartKind.DASHBOARD, BlueprintCategory.BILLING,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("inventory-turnover-dashboard", PartKind.DASHBOARD, BlueprintCategory.INVENTORY,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("warehouse-capacity-dashboard", PartKind.DASHBOARD, BlueprintCategory.INVENTORY,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("logistics-fleet-dashboard", PartKind.DASHBOARD, BlueprintCategory.LOGISTICS,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("delivery-performance-dashboard", PartKind.DASHBOARD, BlueprintCategory.LOGISTICS,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("booking-occupancy-dashboard", PartKind.DASHBOARD, BlueprintCategory.BOOKING,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("event-attendance-dashboard", PartKind.DASHBOARD, BlueprintCategory.EVENTS,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("learning-progress-dashboard", PartKind.DASHBOARD, BlueprintCategory.EDUCATION,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("cohort-performance-dashboard", PartKind.DASHBOARD, BlueprintCategory.EDUCATION,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("hr-workforce-dashboard", PartKind.DASHBOARD, BlueprintCategory.HR,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("recruiting-pipeline-dashboard", PartKind.DASHBOARD, BlueprintCategory.HR,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("developer-usage-dashboard", PartKind.DASHBOARD, BlueprintCategory.DEVELOPER,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("api-reliability-dashboard", PartKind.DASHBOARD, BlueprintCategory.DEVELOPER,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("ai-model-ops-dashboard", PartKind.DASHBOARD, BlueprintCategory.AI,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("prompt-analytics-dashboard", PartKind.DASHBOARD, BlueprintCategory.AI,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("iot-device-fleet-dashboard", PartKind.DASHBOARD, BlueprintCategory.IOT,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("energy-usage-dashboard", PartKind.DASHBOARD, BlueprintCategory.IOT,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("real-estate-portfolio-dashboard", PartKind.DASHBOARD, BlueprintCategory.REAL_ESTATE,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("property-occupancy-dashboard", PartKind.DASHBOARD, BlueprintCategory.REAL_ESTATE,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("community-engagement-dashboard", PartKind.DASHBOARD, BlueprintCategory.COMMUNITY,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("marketplace-liquidity-dashboard", PartKind.DASHBOARD, BlueprintCategory.MARKETPLACE,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("travel-operations-dashboard", PartKind.DASHBOARD, BlueprintCategory.TRAVEL,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("legal-matter-dashboard", PartKind.DASHBOARD, BlueprintCategory.LEGAL,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("media-pipeline-dashboard", PartKind.DASHBOARD, BlueprintCategory.MEDIA,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE)),
            new BlueprintPart("knowledge-health-dashboard", PartKind.DASHBOARD, BlueprintCategory.KNOWLEDGE,
                    Set.of("page.content", "page.main"), Set.of(Purpose.ADMIN, Purpose.PRODUCT_LIKE))
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
