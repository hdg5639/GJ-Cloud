import manifest from "./component-manifest.json";
import type { BlueprintPartDescriptor } from "../core";
import type { GeneratedBlueprintPartId } from "../adapters/generatedPartComponents";

// Blueprint Parts 281종의 단일 정본은 component-manifest.json이다. Java 선택기와 Portal/배포
// Runtime Registry가 모두 같은 파일을 소비하며, 구현 import만 codegen으로 만든다.
export const BLUEPRINT_PARTS = manifest as BlueprintPartDescriptor[];
export type BlueprintPartId = GeneratedBlueprintPartId;

export function blueprintPartsByCategory(category: BlueprintPartDescriptor["category"]) {
  return BLUEPRINT_PARTS.filter((part) => part.category === category);
}

export function blueprintPartsByPurpose(purpose: "ADMIN" | "PRODUCT_LIKE" | "API_TEST") {
  return BLUEPRINT_PARTS.filter((part) => part.preferredPurposes.includes(purpose));
}
