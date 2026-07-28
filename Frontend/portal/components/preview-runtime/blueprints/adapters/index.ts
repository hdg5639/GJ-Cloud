export { CollectionAdapter } from "./CollectionAdapter";
export { DetailBlueprintAdapter } from "./DetailBlueprintAdapter";
export { DashboardAdapter } from "./DashboardAdapter";

// Selector(백엔드)가 componentId를 이 값들로 치환한다. 렌더러가 이 id를 만나면 위 어댑터로 dispatch한다.
// BlueprintPartRegistry.java의 ALL과 동기화(가이드 G1). Phase A 대표 세트.
export const COLLECTION_PART_IDS = ["entity-directory", "kanban-collection", "commerce-product-grid"] as const;
export const DETAIL_PART_IDS = ["infrastructure-resource-detail"] as const;
export const DASHBOARD_PART_IDS = ["operations-health-dashboard"] as const;

export function isCollectionPart(componentId: string): boolean {
  return (COLLECTION_PART_IDS as readonly string[]).includes(componentId);
}
export function isDetailPart(componentId: string): boolean {
  return (DETAIL_PART_IDS as readonly string[]).includes(componentId);
}
export function isDashboardPart(componentId: string): boolean {
  return (DASHBOARD_PART_IDS as readonly string[]).includes(componentId);
}
