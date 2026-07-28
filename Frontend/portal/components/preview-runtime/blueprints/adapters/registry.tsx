import { createElement, type ReactNode } from "react";
import { statusFieldOf } from "../../status";
import type { BlueprintRecord } from "../core";
import { BlueprintKeyValueGrid, blueprintRecordTitle } from "../core";
import type { BlueprintMountPoint, BlueprintPartDescriptor } from "../core";
import manifest from "../manifests/component-manifest.json";
import {
  AlertInbox,
  AuditLogTable,
  CommerceProductGrid,
  CompactMetricTable,
  EntityDirectory,
  KanbanCollection,
  MediaGalleryCollection,
  TimelineCollection,
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
import {
  blueprintComponent,
  type GeneratedBlueprintPartId,
} from "./generatedPartComponents";

const EMPTY_CHART = { labels: [], series: [] };

// component-manifest.json이 계약의 단일 정본이고 generatedPartComponents.ts가 구현 import를 만든다.
// 이 파일의 CUSTOM_RENDERERS는 범용 records/record/metrics 어댑터보다 풍부한 매핑이 필요한 초기
// 파츠만 덮어쓴다. 신규 파츠 등록은 manifest + codegen으로 끝나며 이 파일에 분기를 추가하지 않는다.

export type BlueprintPartKind =
  | "collection"
  | "detail"
  | "dashboard"
  | "action"
  | "modal"
  | "workflow"
  | "form"
  | "layout"
  | "navigation"
  | "feedback"
  | "theme";

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

const CUSTOM_RENDERERS = {
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
  "timeline-collection": {
    kind: "collection",
    render: ({ rows, onRowClick }) => (
      <TimelineCollection
        events={toTimelineEvents(rows)}
        onEventClick={(event) => {
          const row = rowById(rows, event.id);
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
export type BlueprintPartId = GeneratedBlueprintPartId;

const DESCRIPTORS = manifest as BlueprintPartDescriptor[];
const DESCRIPTOR_BY_ID = new Map(DESCRIPTORS.map((part) => [part.componentId, part]));
const CUSTOM_ENTRIES = CUSTOM_RENDERERS as Record<string, BlueprintPartEntry>;

const KIND_BY_MANIFEST_KIND: Record<BlueprintPartDescriptor["kind"], BlueprintPartKind> = {
  ACTION: "action",
  COLLECTION: "collection",
  DASHBOARD: "dashboard",
  DETAIL: "detail",
  FEEDBACK: "feedback",
  FORM: "form",
  LAYOUT: "layout",
  MODAL: "modal",
  NAVIGATION: "navigation",
  THEME: "theme",
  WORKFLOW: "workflow",
};

export function partDescriptor(componentId: string): BlueprintPartDescriptor | undefined {
  return DESCRIPTOR_BY_ID.get(componentId);
}

export function partKind(componentId: string): BlueprintPartKind | undefined {
  const descriptor = partDescriptor(componentId);
  return descriptor ? KIND_BY_MANIFEST_KIND[descriptor.kind] : undefined;
}
export const isCollectionPart = (componentId: string): boolean => partKind(componentId) === "collection";
export const isDetailPart = (componentId: string): boolean => partKind(componentId) === "detail";
export const isDashboardPart = (componentId: string): boolean => partKind(componentId) === "dashboard";
export const isActionPart = (componentId: string): boolean => partKind(componentId) === "action";
export const isOverlayPart = (componentId: string): boolean =>
  ["modal", "workflow", "form"].includes(partKind(componentId) ?? "");

export const BLUEPRINT_PART_LABELS: Record<string, string> = {
  ...Object.fromEntries(DESCRIPTORS.map((part) => [part.componentId, part.label])),
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

const BASE_COMPONENT_MOUNT: Record<string, BlueprintMountPoint> = {
  "resource-table": "COLLECTION",
  "resource-card-grid": "COLLECTION",
  "detail-panel": "DETAIL",
  "full-detail-page": "DETAIL",
  "dashboard-view": "DASHBOARD",
  "recent-activity-dashboard": "DASHBOARD",
  "quick-action-button-group": "ACTIONS",
  "create-edit-modal": "OVERLAY",
  "form-drawer": "OVERLAY",
  "delete-confirm-modal": "OVERLAY",
  "typed-confirm-modal": "OVERLAY",
  "default-layout": "LAYOUT",
  "default-navigation": "NAVIGATION",
  "default-feedback": "FEEDBACK",
  "default-theme": "THEME",
};

export function componentMount(componentId: string): BlueprintMountPoint | undefined {
  return BASE_COMPONENT_MOUNT[componentId] ?? partDescriptor(componentId)?.mountPoint;
}

// 기존 호출부 호환용. 파츠의 시각 kind가 아니라 실제 교체 지점(mount point)을 반환한다.
export const componentKind = componentMount;

export function baseComponentFor(
  mountPoint: BlueprintMountPoint,
  purpose: string | null,
  mode?: string | null,
  slot?: string
): string {
  const productLike = purpose === "PRODUCT_LIKE";
  if (mountPoint === "COLLECTION") return productLike ? "resource-card-grid" : "resource-table";
  if (mountPoint === "DETAIL") return slot === "page.aside" ? "detail-panel" : "full-detail-page";
  if (mountPoint === "DASHBOARD") return productLike ? "recent-activity-dashboard" : "dashboard-view";
  if (mountPoint === "ACTIONS") return "quick-action-button-group";
  if (mountPoint === "OVERLAY") {
    if (mode === "DELETE") return purpose === "ADMIN" ? "typed-confirm-modal" : "delete-confirm-modal";
    return purpose === "PRODUCT_LIKE" ? "form-drawer" : "create-edit-modal";
  }
  if (mountPoint === "LAYOUT") return "default-layout";
  if (mountPoint === "NAVIGATION") return "default-navigation";
  if (mountPoint === "FEEDBACK") return "default-feedback";
  return "default-theme";
}

export function partsForMount(
  mountPoint: BlueprintMountPoint,
  mode?: string | null
): { id: string; label: string; kind: BlueprintPartKind }[] {
  return DESCRIPTORS
    .filter((part) => part.mountPoint === mountPoint)
    .filter((part) => !mode || part.supportedModes.length === 0 || part.supportedModes.includes(mode as "CREATE" | "UPDATE" | "DELETE" | "COMMAND"))
    .map((part) => ({
      id: part.componentId,
      label: BLUEPRINT_PART_LABELS[part.componentId] ?? part.label,
      kind: KIND_BY_MANIFEST_KIND[part.kind],
    }));
}

export function partsForKind(kind: BlueprintPartKind): { id: string; label: string }[] {
  return DESCRIPTORS
    .filter((part) => KIND_BY_MANIFEST_KIND[part.kind] === kind)
    .map((part) => ({ id: part.componentId, label: BLUEPRINT_PART_LABELS[part.componentId] ?? part.label }));
}

export function renderCollectionPart(componentId: string, ctx: CollectionRenderCtx): ReactNode {
  const custom = CUSTOM_ENTRIES[componentId];
  if (custom?.kind === "collection") return custom.render(ctx);
  const Component = blueprintComponent(componentId);
  return Component ? createElement(Component, { records: ctx.rows, onSelect: ctx.onRowClick }) : null;
}
export function renderDetailPart(componentId: string, ctx: DetailRenderCtx): ReactNode {
  const custom = CUSTOM_ENTRIES[componentId];
  if (custom?.kind === "detail") return custom.render(ctx);
  const Component = blueprintComponent(componentId);
  return Component ? createElement(Component, { record: ctx.record, activity: [], actions: [] }) : null;
}
export function renderDashboardPart(componentId: string, ctx: DashboardRenderCtx): ReactNode {
  const custom = CUSTOM_ENTRIES[componentId];
  if (custom?.kind === "dashboard") return custom.render(ctx);
  const Component = blueprintComponent(componentId);
  return Component ? createElement(Component, { metrics: [], records: ctx.rows, activity: [] }) : null;
}

export function renderBlueprintPart(componentId: string, props: Record<string, unknown>): ReactNode {
  const Component = blueprintComponent(componentId);
  return Component ? createElement(Component, props) : null;
}
