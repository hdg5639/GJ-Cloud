import { findCapabilityById, findCapabilityByType } from "./utils";
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
  | "delete-confirm-modal"
  | "dashboard-view";

// list 계열 컴포넌트 중 기본(resource-table) 대신 특정 purpose가 선호하는 Variant.
// Direction Recovery Change Request §10.3 purpose-specific preference 축소판 — Backend
// BlueprintCompiler(Java)와 반드시 동일하게 유지해야 한다(라이브 프리뷰와 실제 배포가 어긋나지 않게).
const LIST_VARIANT_BY_PURPOSE: Partial<Record<Purpose, ComponentId>> = {
  PRODUCT_LIKE: "resource-card-grid",
};

export type SlotId = "page.content" | "page.main" | "page.aside" | "page.overlay";

export interface Block {
  instanceId: string;
  componentId: ComponentId;
  slot: SlotId;
  capabilityIds: string[];
  // create-edit-modal 두 인스턴스(생성/수정)를 구분하는 용도로만 쓴다. 그 외 컴포넌트는 항상 null.
  mode: "CREATE" | "UPDATE" | null;
}

// Backend/Ops의 PreviewBlockResolver와 동일한 규칙 — 지금 렌더러에 하드코딩돼 있던 것을 그대로 데이터로
// 옮긴 것뿐, 동작은 바꾸지 않는다. RESOURCE_LIST와 LIST_DETAIL은 렌더링 규칙이 같아(PageDraftGenerator가
// 타이틀 고를 때만 구분) 같은 분기로 처리한다.
export function resolveBlocks(page: PreviewPage, capabilities: PreviewCapability[]): Block[] {
  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityByType(capabilities, page, "LOGIN");
    if (!login) {
      return [];
    }
    return [{ instanceId: "login", componentId: "login-form", slot: "page.content", capabilityIds: [login.id], mode: null }];
  }

  if (page.skeleton === "DASHBOARD") {
    const listCapabilityIds = page.capabilityIds
      .map((id) => findCapabilityById(capabilities, id))
      .filter((c): c is PreviewCapability => c?.type === "LIST")
      .map((c) => c.id);
    return [
      { instanceId: "dashboard", componentId: "dashboard-view", slot: "page.content", capabilityIds: listCapabilityIds, mode: null },
    ];
  }

  const list = findCapabilityByType(capabilities, page, "LIST");
  if (!list) {
    return [];
  }
  const detail = findCapabilityByType(capabilities, page, "DETAIL");
  const create = findCapabilityByType(capabilities, page, "CREATE");
  const update = findCapabilityByType(capabilities, page, "UPDATE");
  const del = findCapabilityByType(capabilities, page, "DELETE");

  const blocks: Block[] = [
    { instanceId: "list", componentId: "resource-table", slot: "page.main", capabilityIds: [list.id], mode: null },
  ];
  if (detail) {
    blocks.push({ instanceId: "detail", componentId: "detail-panel", slot: "page.aside", capabilityIds: [detail.id], mode: null });
  }
  if (create) {
    blocks.push({
      instanceId: "create",
      componentId: "create-edit-modal",
      slot: "page.overlay",
      capabilityIds: [create.id],
      mode: "CREATE",
    });
  }
  if (update) {
    blocks.push({
      instanceId: "update",
      componentId: "create-edit-modal",
      slot: "page.overlay",
      capabilityIds: [update.id],
      mode: "UPDATE",
    });
  }
  if (del) {
    blocks.push({ instanceId: "delete", componentId: "delete-confirm-modal", slot: "page.overlay", capabilityIds: [del.id], mode: null });
  }
  return blocks;
}

// Backend BlueprintCompiler.compile(...)과 동일한 규칙 — resolveBlocks가 만든 기본 Block 목록에서
// list 계열(resource-table) Block을 purpose가 선호하는 Variant로 교체한다. 다른 Block(로그인/상세/
// 모달/대시보드)은 절대 건드리지 않는다.
export function compileBlocks(blocks: Block[], purpose: Purpose | null): Block[] {
  const preferredListComponentId = purpose ? LIST_VARIANT_BY_PURPOSE[purpose] : undefined;
  if (!preferredListComponentId) {
    return blocks;
  }
  return blocks.map((block) =>
    block.componentId === "resource-table" ? { ...block, componentId: preferredListComponentId } : block
  );
}

// list 계열은 compileBlocks가 purpose에 따라 resource-table/resource-card-grid 중 하나로 이미
// 컴파일해뒀다 — 어느 쪽이든 찾아서 실제로 어떤 컴포넌트를 마운트할지는 호출 측이 componentId를 보고 정한다.
export function findListBlock(blocks: Block[]): Block | undefined {
  return blocks.find((b) => b.componentId === "resource-table" || b.componentId === "resource-card-grid");
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
