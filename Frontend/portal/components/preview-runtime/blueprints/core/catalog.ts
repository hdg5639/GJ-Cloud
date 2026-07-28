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
  | "TRAVEL"
  | "THEME";

export type BlueprintSurface =
  | "page.content"
  | "page.main"
  | "page.primary"
  | "page.aside"
  | "page.secondary"
  | "page.actions"
  | "page.overlay"
  | "page.header"
  | "page.toolbar"
  | "page.navigation"
  | "page.layout"
  | "page.theme"
  | "page.feedback"
  | "table.toolbar"
  | "table.row-actions"
  | "table.empty"
  | "modal.body"
  | "drawer.body";

export type BlueprintPartKind =
  | "LAYOUT"
  | "DASHBOARD"
  | "COLLECTION"
  | "DETAIL"
  | "ACTION"
  | "MODAL"
  | "WORKFLOW"
  | "FORM"
  | "NAVIGATION"
  | "FEEDBACK"
  | "THEME";

export type BlueprintMountPoint =
  | "COLLECTION"
  | "DETAIL"
  | "DASHBOARD"
  | "ACTIONS"
  | "OVERLAY"
  | "LAYOUT"
  | "NAVIGATION"
  | "FEEDBACK"
  | "THEME";

export interface BlueprintPartDescriptor {
  componentId: string;
  exportName: string;
  label: string;
  family: string;
  category: BlueprintCategory;
  kind: BlueprintPartKind;
  mountPoint: BlueprintMountPoint;
  acceptedSurfaces: BlueprintSurface[];
  preferredPurposes: Array<"ADMIN" | "PRODUCT_LIKE" | "API_TEST">;
  supportedModes: Array<"CREATE" | "UPDATE" | "DELETE" | "COMMAND">;
  overlayPresentation: "SELF_HOSTED" | "WRAPPED" | null;
  autoSelectable: boolean;
  sourcePath: string;
  tags: string[];
  states: string[];
  tone?: BlueprintTone;
  theme?: string;
}
