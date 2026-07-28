export { CollectionAdapter } from "./CollectionAdapter";
export { DetailBlueprintAdapter } from "./DetailBlueprintAdapter";
export { DashboardAdapter } from "./DashboardAdapter";

// 파츠 판별/타입은 단일 레지스트리(registry.tsx)에서 파생된다 — 별도 id 목록을 손으로 두지 않는다.
export {
  isCollectionPart,
  isDetailPart,
  isDashboardPart,
  isActionPart,
  isOverlayPart,
  partKind,
  partDescriptor,
  componentKind,
  componentMount,
  baseComponentFor,
  partsForKind,
  partsForMount,
  BLUEPRINT_PART_LABELS,
  renderBlueprintPart,
  type BlueprintPartId,
  type BlueprintPartKind,
} from "./registry";
