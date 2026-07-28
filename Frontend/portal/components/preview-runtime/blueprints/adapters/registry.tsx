import type { ReactNode } from "react";
import { statusFieldOf } from "../../status";
import type { BlueprintRecord } from "../core";
import { BlueprintKeyValueGrid, blueprintRecordTitle } from "../core";
import {
  AlertInbox,
  AuditLogTable,
  CommerceProductGrid,
  CompactMetricTable,
  EntityDirectory,
  KanbanCollection,
  MediaGalleryCollection,
} from "../collections";
import {
  CommerceOrderDetail,
  ContentArticleDetail,
  CustomerProfileDetail,
  IncidentDetail,
  InfrastructureResourceDetail,
  SettingsDetail,
} from "../details";
import {
  AdminGovernanceDashboard,
  CommerceRevenueDashboard,
  ContentPerformanceDashboard,
  ExecutiveKpiDashboard,
  OperationsHealthDashboard,
  ProjectDeliveryDashboard,
} from "../dashboards";
import {
  rowById,
  toAlerts,
  toDirectoryEntries,
  toFields,
  toKanbanColumns,
  toMediaItems,
  toTimelineEvents,
} from "./map";

const EMPTY_CHART = { labels: [], series: [] };

// ─────────────────────────────────────────────────────────────────────────────
// Blueprint 파츠 단일 레지스트리 (프론트 정본)
//
// 새 파츠를 배선할 때 프론트에서 **여기 한 곳만** 고치면 된다. 이 맵에서 아래가 전부 파생된다:
//   - ComponentId union (blueprint.ts의 `| BlueprintPartId`)
//   - isCollectionPart / isDetailPart / isDashboardPart 판별
//   - blueprint.ts의 findList/Detail/DashboardBlock 인식
//   - PreviewPageRenderer의 dispatch (render* 함수)
//
// 대응하는 백엔드도 한 줄만 추가한다: BlueprintPartRegistry.java의 ALL(카테고리·slot·kind 매핑).
// 즉 파츠 하나 = 프론트 1줄 + 백엔드 1줄. (componentId 문자열이 두 곳에서 동일해야 함)
// ─────────────────────────────────────────────────────────────────────────────

export type BlueprintPartKind = "collection" | "detail" | "dashboard";

// 어댑터가 fetch해서 넘기는 렌더 컨텍스트 — kind별로 다르다.
export interface CollectionRenderCtx {
  rows: BlueprintRecord[];
  onRowClick?: (row: BlueprintRecord) => void;
}
export interface DetailRenderCtx {
  record: BlueprintRecord;
}
export interface DashboardRenderCtx {
  rows: BlueprintRecord[];
}

type BlueprintPartEntry =
  | { kind: "collection"; render: (ctx: CollectionRenderCtx) => ReactNode }
  | { kind: "detail"; render: (ctx: DetailRenderCtx) => ReactNode }
  | { kind: "dashboard"; render: (ctx: DashboardRenderCtx) => ReactNode };

// ▼▼▼ 새 파츠는 여기 한 줄 추가 ▼▼▼
export const BLUEPRINT_PARTS = {
  "entity-directory": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <EntityDirectory
        entries={toDirectoryEntries(rows)}
        onSelect={(entry) => {
          const row = rowById(rows, entry.id);
          if (row) onRowClick?.(row);
        }}
      />
    ),
  },
  "kanban-collection": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <KanbanCollection
        columns={toKanbanColumns(rows)}
        onCardClick={(card) => {
          const row = rowById(rows, card.id);
          if (row) onRowClick?.(row);
        }}
      />
    ),
  },
  "commerce-product-grid": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <CommerceProductGrid products={rows} onSelect={(product) => onRowClick?.(product)} />
    ),
  },
  "infrastructure-resource-detail": {
    kind: "detail",
    render: ({ record }) => (
      <InfrastructureResourceDetail
        resource={record}
        metrics={[]}
        fields={toFields(record, [statusFieldOf(record) ?? ""])}
        actions={[]}
      />
    ),
  },
  "operations-health-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => <OperationsHealthDashboard metrics={[]} services={rows} incidents={[]} />,
  },
  // ── Phase D: category별 확장 ────────────────────────────────────────────────
  "alert-inbox": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <AlertInbox
        alerts={toAlerts(rows)}
        onOpen={(alert) => {
          const row = rowById(rows, alert.id);
          if (row) onRowClick?.(row);
        }}
      />
    ),
  },
  "audit-log-table": {
    kind: "collection",
    render: ({ rows, onRowClick }) => <AuditLogTable entries={rows} onSelect={(entry) => onRowClick?.(entry)} />,
  },
  "compact-metric-table": {
    kind: "collection",
    render: ({ rows, onRowClick }) => <CompactMetricTable rows={rows} onSelect={(row) => onRowClick?.(row)} />,
  },
  "media-gallery-collection": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <MediaGalleryCollection
        items={toMediaItems(rows)}
        onSelect={(item) => {
          const row = rowById(rows, item.id);
          if (row) onRowClick?.(row);
        }}
      />
    ),
  },
  "commerce-order-detail": {
    kind: "detail",
    render: ({ record }) => (
      <CommerceOrderDetail
        order={record}
        customerFields={[]}
        items={[]}
        paymentFields={toFields(record, [statusFieldOf(record) ?? ""])}
        fulfillment={[]}
        actions={[]}
      />
    ),
  },
  "content-article-detail": {
    kind: "detail",
    render: ({ record }) => (
      <ContentArticleDetail article={record} metadata={toFields(record, [statusFieldOf(record) ?? ""])} actions={[]} />
    ),
  },
  "customer-profile-detail": {
    kind: "detail",
    render: ({ record }) => (
      <CustomerProfileDetail
        profile={record}
        metrics={[]}
        fields={toFields(record, [statusFieldOf(record) ?? ""])}
        actions={[]}
      />
    ),
  },
  "incident-detail": {
    kind: "detail",
    render: ({ record }) => (
      <IncidentDetail
        incident={record}
        metrics={[]}
        fields={toFields(record, [statusFieldOf(record) ?? ""])}
        actions={[]}
        timeline={[]}
      />
    ),
  },
  "settings-detail": {
    kind: "detail",
    render: ({ record }) => (
      <SettingsDetail
        sections={[
          {
            id: "details",
            title: blueprintRecordTitle(record),
            content: <BlueprintKeyValueGrid fields={toFields(record)} />,
          },
        ]}
      />
    ),
  },
  "admin-governance-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => <AdminGovernanceDashboard metrics={[]} policyFindings={[]} privilegedAccounts={rows} />,
  },
  "commerce-revenue-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => (
      <CommerceRevenueDashboard metrics={[]} revenue={EMPTY_CHART} topProducts={rows} orders={rows} />
    ),
  },
  "content-performance-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => <ContentPerformanceDashboard metrics={[]} traffic={EMPTY_CHART} content={rows} />,
  },
  "executive-kpi-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => <ExecutiveKpiDashboard metrics={[]} highlights={toTimelineEvents(rows)} />,
  },
  "project-delivery-dashboard": {
    kind: "dashboard",
    render: ({ rows }) => <ProjectDeliveryDashboard metrics={[]} milestones={rows} activity={[]} />,
  },
} satisfies Record<string, BlueprintPartEntry>;
// ▲▲▲

export type BlueprintPartId = keyof typeof BLUEPRINT_PARTS;

const ENTRIES = BLUEPRINT_PARTS as Record<string, BlueprintPartEntry>;

export function partKind(componentId: string): BlueprintPartKind | undefined {
  return ENTRIES[componentId]?.kind;
}
export const isCollectionPart = (componentId: string): boolean => partKind(componentId) === "collection";
export const isDetailPart = (componentId: string): boolean => partKind(componentId) === "detail";
export const isDashboardPart = (componentId: string): boolean => partKind(componentId) === "dashboard";

// 사용자 오버라이드 UI(마법사 파츠 선택기)용 메타 — 파츠 id → 사람이 읽는 이름.
export const BLUEPRINT_PART_LABELS: Record<string, string> = {
  "entity-directory": "디렉토리",
  "kanban-collection": "칸반 보드",
  "commerce-product-grid": "상품 그리드",
  "infrastructure-resource-detail": "인프라 상세",
  "operations-health-dashboard": "운영 상태 대시보드",
  "alert-inbox": "경보 인박스",
  "audit-log-table": "감사 로그 테이블",
  "compact-metric-table": "지표 테이블",
  "media-gallery-collection": "미디어 갤러리",
  "commerce-order-detail": "주문 상세",
  "content-article-detail": "콘텐츠 상세",
  "customer-profile-detail": "고객 프로필",
  "incident-detail": "인시던트 상세",
  "settings-detail": "설정 상세",
  "admin-governance-dashboard": "거버넌스 대시보드",
  "commerce-revenue-dashboard": "매출 대시보드",
  "content-performance-dashboard": "콘텐츠 성과 대시보드",
  "executive-kpi-dashboard": "경영 KPI 대시보드",
  "project-delivery-dashboard": "프로젝트 배송 대시보드",
};

// 기본 컴포넌트 id → kind(백엔드 BlueprintPartRegistry.kindOfBaseComponent와 동일). 파츠 id는 partKind로.
const BASE_COMPONENT_KIND: Record<string, BlueprintPartKind> = {
  "resource-table": "collection",
  "resource-card-grid": "collection",
  "full-detail-page": "detail",
  "dashboard-view": "dashboard",
  "recent-activity-dashboard": "dashboard",
};

// Block의 현재 componentId(기본 또는 파츠)가 스왑 대상 kind면 그 kind를, 아니면 undefined.
export function componentKind(componentId: string): BlueprintPartKind | undefined {
  return BASE_COMPONENT_KIND[componentId] ?? partKind(componentId);
}

// purpose 기준 그 kind의 '기본' 컴포넌트 id(오버라이드에서 "기본 유지"를 표현할 때 쓴다).
export function baseComponentFor(kind: BlueprintPartKind, purpose: string | null): string {
  const productLike = purpose === "PRODUCT_LIKE";
  if (kind === "collection") return productLike ? "resource-card-grid" : "resource-table";
  if (kind === "dashboard") return productLike ? "recent-activity-dashboard" : "dashboard-view";
  return "full-detail-page";
}

export function partsForKind(kind: BlueprintPartKind): { id: string; label: string }[] {
  return Object.entries(BLUEPRINT_PARTS)
    .filter(([, entry]) => (entry as { kind: BlueprintPartKind }).kind === kind)
    .map(([id]) => ({ id, label: BLUEPRINT_PART_LABELS[id] ?? id }));
}

export function renderCollectionPart(componentId: string, ctx: CollectionRenderCtx): ReactNode {
  const entry = ENTRIES[componentId];
  return entry && entry.kind === "collection" ? entry.render(ctx) : null;
}
export function renderDetailPart(componentId: string, ctx: DetailRenderCtx): ReactNode {
  const entry = ENTRIES[componentId];
  return entry && entry.kind === "detail" ? entry.render(ctx) : null;
}
export function renderDashboardPart(componentId: string, ctx: DashboardRenderCtx): ReactNode {
  const entry = ENTRIES[componentId];
  return entry && entry.kind === "dashboard" ? entry.render(ctx) : null;
}
