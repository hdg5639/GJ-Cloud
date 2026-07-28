import type { ReactNode } from "react";
import { statusFieldOf } from "../../status";
import type { BlueprintRecord } from "../core";
import { CommerceProductGrid, EntityDirectory, KanbanCollection } from "../collections";
import { InfrastructureResourceDetail } from "../details";
import { OperationsHealthDashboard } from "../dashboards";
import { rowById, toDirectoryEntries, toFields, toKanbanColumns } from "./map";

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
