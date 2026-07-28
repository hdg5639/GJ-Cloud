import { findCapabilityById, findCapabilityByType } from "./utils";
import {
  isCollectionPart,
  isDashboardPart,
  isDetailPart,
  isOverlayPart,
  type BlueprintPartId,
} from "./blueprints/adapters";
import type { PreviewCapability, PreviewPage, Purpose } from "./types";

// auto-preview-design/01-blueprint-schema.md의 Block Instance 축소판 — PreviewPageRenderer가
// page.skeleton을 직접 switch하는 대신 이 목록을 순회해 조립한다. Registry가 없으므로
// componentRef의 contractVersion/implementationVersion pin, bindingRefs는 두지 않는다 —
// capabilityIds만으로 지금 렌더러가 필요한 정보는 충분하다.
export type ComponentId =
  | "login-form"
  | "resource-table"
  | "resource-card-grid"
  | "detail-panel"
  | "create-edit-modal"
  | "form-drawer"
  | "delete-confirm-modal"
  | "typed-confirm-modal"
  | "dashboard-view"
  | "recent-activity-dashboard"
  | "quick-action-button-group"
  | "full-detail-page"
  | "child-resource-list"
  | "default-layout"
  | "default-navigation"
  | "default-feedback"
  | "default-theme"
  // 파츠 id 집합은 component-manifest.json에서 생성된 Registry 타입으로부터 파생된다.
  // 백엔드도 같은 JSON을 읽으므로 신규 파츠 추가 시 이 union이나 Java 목록을 따로 수정하지 않는다.
  | BlueprintPartId;

// 계열(같은 Slot·Capability 요구조건을 공유하는 Variant 묶음)마다 기본 componentId를 키로, 특정
// purpose가 선호하는 Variant를 값으로 둔다. Direction Recovery Change Request §10.3
// purpose-specific preference 축소판 — Backend BlueprintCompiler(Java)와 반드시 동일하게 유지해야
// 한다(라이브 프리뷰와 실제 배포가 어긋나지 않게).
const VARIANT_BY_PURPOSE: Partial<Record<ComponentId, Partial<Record<Purpose, ComponentId>>>> = {
  "resource-table": { PRODUCT_LIKE: "resource-card-grid" },
  // Change Request §3 "Administrator purpose — Destructive-operation safeguards".
  "delete-confirm-modal": { ADMIN: "typed-confirm-modal" },
  // Change Request §3 "Product-like purpose — ... drawers and guided creation flows".
  "create-edit-modal": { PRODUCT_LIKE: "form-drawer" },
  // §9.5 dashboard 계열 두 번째 Variant — PRODUCT_LIKE는 개수 카드보다 최근 항목 피드를 선호.
  "dashboard-view": { PRODUCT_LIKE: "recent-activity-dashboard" },
  // §9.2 detail 계열 두 번째 Variant — PRODUCT_LIKE는 사이드 패널보다 전체 페이지 상세를 선호.
  "detail-panel": { PRODUCT_LIKE: "full-detail-page" },
};

// detail 계열만 예외적으로 Slot 자체가 바뀐다 — 선택된 리소스의 상세를 사이드 칼럼이 아니라 전체
// 폭으로 보여주려면 목록(Block "list")이 차지하던 자리(page.main)를 대신 차지해야 한다. Backend
// BlueprintCompiler의 SLOT_OVERRIDE/REPLACES_OVERRIDE와 반드시 동일하게 유지해야 한다.
const SLOT_OVERRIDE: Partial<Record<ComponentId, SlotId>> = { "full-detail-page": "page.main" };
const REPLACES_OVERRIDE: Partial<Record<ComponentId, string>> = { "full-detail-page": "list" };

export type SlotId =
  | "page.content"
  | "page.main"
  | "page.primary"
  | "page.aside"
  | "page.overlay"
  | "page.actions"
  | "page.secondary"
  | "page.layout"
  | "page.navigation"
  | "page.feedback"
  | "page.theme";

export interface Block {
  instanceId: string;
  componentId: ComponentId;
  slot: SlotId;
  capabilityIds: string[];
  // create-edit-modal 두 인스턴스(생성/수정)를 구분하는 용도로만 쓴다. 그 외 컴포넌트는 항상 null.
  mode: "CREATE" | "UPDATE" | "DELETE" | "COMMAND" | null;
  // 이 Block이 활성화됐을 때(예: 행 선택) 같은 페이지의 다른 Block(주로 같은 Slot을 두고 다투는 대안)
  // 자리를 대신 차지한다는 표시(그 Block의 instanceId). null이면 지금까지처럼 독립적으로 존재.
  replaces: string | null;
}

function withPageChrome(page: PreviewPage, blocks: Block[]): Block[] {
  if (blocks.length === 0) {
    return blocks;
  }
  const capabilityIds = page.capabilityIds.length > 0 ? [page.capabilityIds[0]] : [];
  return [
    ...blocks,
    { instanceId: "layout", componentId: "default-layout", slot: "page.layout", capabilityIds, mode: null, replaces: null },
    { instanceId: "navigation", componentId: "default-navigation", slot: "page.navigation", capabilityIds, mode: null, replaces: null },
    { instanceId: "feedback", componentId: "default-feedback", slot: "page.feedback", capabilityIds, mode: null, replaces: null },
    { instanceId: "theme", componentId: "default-theme", slot: "page.theme", capabilityIds, mode: null, replaces: null },
  ];
}

// Backend/Ops의 PreviewBlockResolver와 동일한 규칙. Direction Recovery Change Request §13.1 — 마법사
// 라이브 프리뷰(PreviewPageRenderer)는 이 함수를 더 이상 호출하지 않는다(POST /ops/preview/blocks가
// 계산한 Block을 그대로 받아씀). 여기 남아있는 이유는 app/preview-demo(백엔드 없이 목데이터로 렌더러만
// 확인하는 프론트 전용 샌드박스, 프로덕션 라우트 아님)가 여전히 이 함수로 로컬 Block을 만들기 때문 —
// Backend 로직이 바뀌면 이 함수도 반드시 같이 맞춰야 한다(샌드박스가 실제와 어긋나지 않도록).
export function resolveBlocks(page: PreviewPage, capabilities: PreviewCapability[]): Block[] {
  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityByType(capabilities, page, "LOGIN");
    if (!login) {
      return [];
    }
    return [{ instanceId: "login", componentId: "login-form", slot: "page.content", capabilityIds: [login.id], mode: null, replaces: null }];
  }

  if (page.skeleton === "DASHBOARD") {
    const listCapabilityIds = page.capabilityIds
      .map((id) => findCapabilityById(capabilities, id))
      .filter((c): c is PreviewCapability => c?.type === "LIST")
      .map((c) => c.id);
    return withPageChrome(page, [
      {
        instanceId: "dashboard",
        componentId: "dashboard-view",
        slot: "page.content",
        capabilityIds: listCapabilityIds,
        mode: null,
        replaces: null,
      },
    ]);
  }

  if (page.skeleton === "RESOURCE_DETAIL") {
    const pageCapabilities = page.capabilityIds
      .map((id) => findCapabilityById(capabilities, id))
      .filter((capability): capability is PreviewCapability => capability !== undefined);
    const detail = pageCapabilities.find((capability) => capability.type === "DETAIL");
    if (!detail) return [];
    const blocks: Block[] = [
      {
        instanceId: "detail",
        componentId: "full-detail-page",
        slot: "page.primary",
        capabilityIds: [detail.id],
        mode: null,
        replaces: null,
      },
    ];
    const commandIds = pageCapabilities
      .filter((capability) => capability.kind === "COMMAND")
      .map((capability) => capability.id);
    if (commandIds.length > 0) {
      blocks.push({
        instanceId: "actions",
        componentId: "quick-action-button-group",
        slot: "page.actions",
        capabilityIds: commandIds,
        mode: "COMMAND",
        replaces: null,
      });
    }
    const update = pageCapabilities.find(
      (capability) => capability.type === "UPDATE" && capability.resourceName === detail.resourceName
    );
    const del = pageCapabilities.find(
      (capability) => capability.type === "DELETE" && capability.resourceName === detail.resourceName
    );
    if (update) {
      blocks.push({
        instanceId: "update",
        componentId: "create-edit-modal",
        slot: "page.overlay",
        capabilityIds: [update.id],
        mode: "UPDATE",
        replaces: null,
      });
    }
    if (del) {
      blocks.push({
        instanceId: "delete",
        componentId: "delete-confirm-modal",
        slot: "page.overlay",
        capabilityIds: [del.id],
        mode: "DELETE",
        replaces: null,
      });
    }
    const lastParameter = detail.path.lastIndexOf("/{");
    const parentPrefix = lastParameter >= 0 ? detail.path.slice(0, lastParameter) : null;
    for (const childList of pageCapabilities.filter(
      (capability) =>
        capability.type === "LIST"
        && parentPrefix !== null
        && capability.path.startsWith(`${parentPrefix}/{`)
        && capability.path !== detail.path
    )) {
      const childActions = pageCapabilities
        .filter(
          (capability) =>
            ["CREATE", "UPDATE", "DELETE"].includes(capability.type ?? "")
            && capability.resourceName === childList.resourceName
            && parentPrefix !== null
            && capability.path.startsWith(`${parentPrefix}/{`)
        )
        .map((capability) => capability.id);
      blocks.push({
        instanceId: `child-${childList.resourceName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")}`,
        componentId: "child-resource-list",
        slot: "page.secondary",
        capabilityIds: [childList.id, ...childActions],
        mode: null,
        replaces: null,
      });
    }
    return withPageChrome(page, blocks);
  }

  const list = findCapabilityByType(capabilities, page, "LIST");
  if (!list) {
    return [];
  }
  const pageCapabilities = page.capabilityIds
    .map((id) => findCapabilityById(capabilities, id))
    .filter((capability): capability is PreviewCapability => capability !== undefined);
  const detail = pageCapabilities.find((capability) => capability.type === "DETAIL" && capability.resourceName === list.resourceName);
  const create = pageCapabilities.find((capability) => capability.type === "CREATE" && capability.resourceName === list.resourceName);
  const update = pageCapabilities.find((capability) => capability.type === "UPDATE" && capability.resourceName === list.resourceName);
  const del = pageCapabilities.find((capability) => capability.type === "DELETE" && capability.resourceName === list.resourceName);

  const blocks: Block[] = [
    { instanceId: "list", componentId: "resource-table", slot: "page.main", capabilityIds: [list.id], mode: null, replaces: null },
  ];
  if (detail) {
    blocks.push({
      instanceId: "detail",
      componentId: "detail-panel",
      slot: "page.aside",
      capabilityIds: [detail.id],
      mode: null,
      replaces: null,
    });
  }
  if (create) {
    blocks.push({
      instanceId: "create",
      componentId: "create-edit-modal",
      slot: "page.overlay",
      capabilityIds: [create.id],
      mode: "CREATE",
      replaces: null,
    });
  }
  if (update) {
    blocks.push({
      instanceId: "update",
      componentId: "create-edit-modal",
      slot: "page.overlay",
      capabilityIds: [update.id],
      mode: "UPDATE",
      replaces: null,
    });
  }
  if (del) {
    blocks.push({
      instanceId: "delete",
      componentId: "delete-confirm-modal",
      slot: "page.overlay",
      capabilityIds: [del.id],
      mode: "DELETE",
      replaces: null,
    });
  }

  // Backend PreviewBlockResolver.resolveDefault와 동일한 규칙 — COMMAND capability(vm.start 등)를
  // discard하지 않고 page.actions Block으로 노출한다. 리소스 하나에 여러 개 있을 수 있어 한 Block에
  // 전부 담는다(dashboard-view와 동일 패턴).
  const commandIds = page.capabilityIds
    .map((id) => findCapabilityById(capabilities, id))
    .filter((c): c is PreviewCapability => c?.kind === "COMMAND")
    .map((c) => c.id);
  if (commandIds.length > 0) {
    blocks.push({
      instanceId: "actions",
      componentId: "quick-action-button-group",
      slot: "page.actions",
      capabilityIds: commandIds,
      mode: "COMMAND",
      replaces: null,
    });
  }

  const listCapabilities = page.capabilityIds
    .map((id) => findCapabilityById(capabilities, id))
    .filter((capability): capability is PreviewCapability => capability?.type === "LIST");
  const parentPath = list.path.replace(/\/$/, "");
  for (const childList of listCapabilities.slice(1)) {
    if (!childList.path.startsWith(`${parentPath}/{`)) continue;
    const childCreate = page.capabilityIds
      .map((id) => findCapabilityById(capabilities, id))
      .find(
        (capability) =>
          capability?.type === "CREATE" &&
          capability.resourceName === childList.resourceName &&
          capability.path.startsWith(`${parentPath}/{`)
      );
    blocks.push({
      instanceId: `child-${childList.resourceName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`,
      componentId: "child-resource-list",
      slot: "page.secondary",
      capabilityIds: [childList.id, ...(childCreate ? [childCreate.id] : [])],
      mode: null,
      replaces: null,
    });
  }
  return withPageChrome(page, blocks);
}

// Backend BlueprintCompiler.compile(...)과 동일한 규칙 — resolveBlocks가 만든 기본 Block 목록에서
// 각 Block을 purpose가 선호하는 Variant로 교체한다(계열이 하나도 안 걸리면 그대로 둠). resolveBlocks와
// 마찬가지로 실제 서비스 경로에서는 안 쓰이고 app/preview-demo 샌드박스 전용으로만 남아있다.
export function compileBlocks(blocks: Block[], purpose: Purpose | null): Block[] {
  if (!purpose) {
    return blocks;
  }
  return blocks.map((block) => {
    const preferredComponentId = VARIANT_BY_PURPOSE[block.componentId]?.[purpose];
    if (!preferredComponentId) {
      return block;
    }
    return {
      ...block,
      componentId: preferredComponentId,
      slot: SLOT_OVERRIDE[preferredComponentId] ?? block.slot,
      replaces: REPLACES_OVERRIDE[preferredComponentId] ?? null,
    };
  });
}

// list 계열은 compileBlocks가 purpose에 따라 resource-table/resource-card-grid 중 하나로 이미
// 컴파일해뒀다 — 어느 쪽이든 찾아서 실제로 어떤 컴포넌트를 마운트할지는 호출 측이 componentId를 보고 정한다.
export function findListBlock(blocks: Block[]): Block | undefined {
  return blocks.find((b) => b.componentId === "resource-table" || b.componentId === "resource-card-grid"
    || isCollectionPart(b.componentId));
}

// dashboard 계열도 마찬가지 — compileBlocks가 purpose(PRODUCT_LIKE)에 따라 dashboard-view/
// recent-activity-dashboard 중 하나로 이미 컴파일해뒀다.
export function findDashboardBlock(blocks: Block[]): Block | undefined {
  return blocks.find((b) => b.componentId === "dashboard-view" || b.componentId === "recent-activity-dashboard"
    || isDashboardPart(b.componentId));
}

// detail 계열도 마찬가지 — compileBlocks가 purpose(PRODUCT_LIKE)에 따라 detail-panel/
// full-detail-page 중 하나로 이미 컴파일해뒀다.
export function findDetailBlock(blocks: Block[]): Block | undefined {
  return blocks.find((b) => b.componentId === "detail-panel" || b.componentId === "full-detail-page"
    || isDetailPart(b.componentId));
}

// destructive 계열도 마찬가지 — compileBlocks가 purpose(ADMIN)에 따라 delete-confirm-modal/
// typed-confirm-modal 중 하나로 이미 컴파일해뒀다.
export function findDeleteBlock(blocks: Block[]): Block | undefined {
  return blocks.find((b) =>
    b.mode === "DELETE"
    && (b.componentId === "delete-confirm-modal" || b.componentId === "typed-confirm-modal" || isOverlayPart(b.componentId))
  );
}

// create/edit 계열도 마찬가지 — compileBlocks가 purpose(PRODUCT_LIKE)에 따라 create-edit-modal/
// form-drawer 중 하나로 이미 컴파일해뒀다. mode로 생성/수정 인스턴스를 구분한다.
export function findCreateEditBlock(blocks: Block[], mode: "CREATE" | "UPDATE"): Block | undefined {
  return blocks.find((b) =>
    b.mode === mode
    && (b.componentId === "create-edit-modal" || b.componentId === "form-drawer" || isOverlayPart(b.componentId))
  );
}

// blocks에서 특정 componentId(+선택적으로 mode)를 가진 block 하나를 찾아 그 block이 가리키는 첫
// capability를 반환한다. PreviewPageRenderer가 예전에 findCapabilityByType(capabilities, page, "X")로
// 하던 일을 이제 resolveBlocks가 미리 계산해둔 Block 목록에서 찾는 것으로 대체한다.
export function findCapabilityForBlock(
  blocks: Block[],
  capabilities: PreviewCapability[],
  componentId: ComponentId,
  mode?: "CREATE" | "UPDATE"
): PreviewCapability | undefined {
  const block = blocks.find((b) => b.componentId === componentId && (mode === undefined || b.mode === mode));
  const capabilityId = block?.capabilityIds[0];
  return capabilityId ? findCapabilityById(capabilities, capabilityId) : undefined;
}
