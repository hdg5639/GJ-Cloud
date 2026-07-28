import type { BlueprintTone } from "./types";

export type BlueprintCategory =
  | "ADMIN"
  | "ANALYTICS"
  | "COMMERCE"
  | "CONTENT"
  | "CRM"
  | "INFRASTRUCTURE"
  | "OBSERVABILITY"
  | "PROJECT"
  | "SETTINGS"
  | "WORKFLOW"
  // Expansion Pack 신규 도메인(백엔드 BlueprintCategory enum과 1:1).
  | "AI"
  | "BILLING"
  | "BOOKING"
  | "COMMUNITY"
  | "DEVELOPER"
  | "EDUCATION"
  | "EVENTS"
  | "FINANCE"
  | "HR"
  | "INVENTORY"
  | "IOT"
  | "KNOWLEDGE"
  | "LEGAL"
  | "LOGISTICS"
  | "MARKETPLACE"
  | "MEDIA"
  | "REAL_ESTATE"
  | "SECURITY"
  | "SUPPORT"
  | "TRAVEL";

export type BlueprintSurface =
  | "page.content"
  | "page.main"
  | "page.aside"
  | "page.secondary"
  | "page.actions"
  | "page.overlay"
  | "modal.body"
  | "drawer.body";

export interface BlueprintPartDescriptor {
  componentId: string;
  exportName: string;
  family: string;
  category: BlueprintCategory;
  kind: "LAYOUT" | "DASHBOARD" | "COLLECTION" | "DETAIL" | "ACTION" | "MODAL" | "WORKFLOW";
  acceptedSurfaces: BlueprintSurface[];
  preferredPurposes: Array<"ADMIN" | "PRODUCT_LIKE" | "API_TEST">;
  tags: string[];
  states: string[];
  tone?: BlueprintTone;
}
