import type { BlueprintPartDescriptor } from "../core";

export const BLUEPRINT_PARTS = [
  {
    "componentId": "admin-workspace-layout",
    "exportName": "AdminWorkspaceLayout",
    "family": "workspace-shell",
    "category": "ADMIN",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN"
    ],
    "tags": [
      "admin",
      "sidebar",
      "dense"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "analytics-dashboard-layout",
    "exportName": "AnalyticsDashboardLayout",
    "family": "dashboard-layout",
    "category": "ANALYTICS",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "analytics",
      "metrics",
      "charts"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "commerce-catalog-layout",
    "exportName": "CommerceCatalogLayout",
    "family": "catalog-layout",
    "category": "COMMERCE",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE"
    ],
    "tags": [
      "commerce",
      "catalog",
      "cart"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "content-studio-layout",
    "exportName": "ContentStudioLayout",
    "family": "studio-layout",
    "category": "CONTENT",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "content",
      "editor",
      "preview"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "master-detail-layout",
    "exportName": "MasterDetailLayout",
    "family": "master-detail-layout",
    "category": "ADMIN",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "API_TEST"
    ],
    "tags": [
      "master-detail",
      "list",
      "detail"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "operations-cockpit-layout",
    "exportName": "OperationsCockpitLayout",
    "family": "operations-layout",
    "category": "OBSERVABILITY",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "operations",
      "health",
      "events"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "settings-workbench-layout",
    "exportName": "SettingsWorkbenchLayout",
    "family": "settings-layout",
    "category": "SETTINGS",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "settings",
      "navigation",
      "forms"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "workflow-stage-layout",
    "exportName": "WorkflowStageLayout",
    "family": "workflow-layout",
    "category": "WORKFLOW",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "workflow",
      "steps",
      "progress"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "executive-kpi-dashboard",
    "exportName": "ExecutiveKpiDashboard",
    "family": "dashboard",
    "category": "ANALYTICS",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "kpi",
      "executive",
      "trend"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "operations-health-dashboard",
    "exportName": "OperationsHealthDashboard",
    "family": "dashboard",
    "category": "OBSERVABILITY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "operations",
      "service-health",
      "incidents"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "commerce-revenue-dashboard",
    "exportName": "CommerceRevenueDashboard",
    "family": "dashboard",
    "category": "COMMERCE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "commerce",
      "revenue",
      "orders"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "content-performance-dashboard",
    "exportName": "ContentPerformanceDashboard",
    "family": "dashboard",
    "category": "CONTENT",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "content",
      "audience",
      "publishing"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "admin-governance-dashboard",
    "exportName": "AdminGovernanceDashboard",
    "family": "dashboard",
    "category": "ADMIN",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN"
    ],
    "tags": [
      "governance",
      "policy",
      "security"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "project-delivery-dashboard",
    "exportName": "ProjectDeliveryDashboard",
    "family": "dashboard",
    "category": "PROJECT",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "project",
      "milestones",
      "activity"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "kanban-collection",
    "exportName": "KanbanCollection",
    "family": "collection",
    "category": "PROJECT",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "kanban",
      "workflow",
      "cards"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "timeline-collection",
    "exportName": "TimelineCollection",
    "family": "collection",
    "category": "OBSERVABILITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.secondary",
      "page.aside"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "timeline",
      "events",
      "activity"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "media-gallery-collection",
    "exportName": "MediaGalleryCollection",
    "family": "collection",
    "category": "CONTENT",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "gallery",
      "assets"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "entity-directory",
    "exportName": "EntityDirectory",
    "family": "collection",
    "category": "CRM",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "people",
      "directory",
      "cards"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "alert-inbox",
    "exportName": "AlertInbox",
    "family": "collection",
    "category": "OBSERVABILITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "alerts",
      "incidents",
      "acknowledge"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "commerce-product-grid",
    "exportName": "CommerceProductGrid",
    "family": "collection",
    "category": "COMMERCE",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE"
    ],
    "tags": [
      "commerce",
      "products",
      "grid"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "audit-log-table",
    "exportName": "AuditLogTable",
    "family": "collection",
    "category": "ADMIN",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "API_TEST"
    ],
    "tags": [
      "audit",
      "table",
      "security"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "compact-metric-table",
    "exportName": "CompactMetricTable",
    "family": "collection",
    "category": "ANALYTICS",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "metrics",
      "table",
      "sparkline"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "infrastructure-resource-detail",
    "exportName": "InfrastructureResourceDetail",
    "family": "detail",
    "category": "INFRASTRUCTURE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "infrastructure",
      "status",
      "commands"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "commerce-order-detail",
    "exportName": "CommerceOrderDetail",
    "family": "detail",
    "category": "COMMERCE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "commerce",
      "order",
      "fulfillment"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "customer-profile-detail",
    "exportName": "CustomerProfileDetail",
    "family": "detail",
    "category": "CRM",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "customer",
      "profile",
      "journey"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "content-article-detail",
    "exportName": "ContentArticleDetail",
    "family": "detail",
    "category": "CONTENT",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "content",
      "publishing",
      "revision"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "incident-detail",
    "exportName": "IncidentDetail",
    "family": "detail",
    "category": "OBSERVABILITY",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "incident",
      "timeline",
      "runbook"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "settings-detail",
    "exportName": "SettingsDetail",
    "family": "detail",
    "category": "SETTINGS",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "settings",
      "permissions",
      "sections"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "typed-danger-modal",
    "exportName": "TypedDangerModal",
    "family": "destructive",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "danger",
      "typed-confirmation",
      "irreversible"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "bulk-action-modal",
    "exportName": "BulkActionModal",
    "family": "bulk-action",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN"
    ],
    "tags": [
      "bulk",
      "action",
      "audit"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "import-data-modal",
    "exportName": "ImportDataModal",
    "family": "data-transfer",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "API_TEST"
    ],
    "tags": [
      "import",
      "file",
      "validate"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "export-data-modal",
    "exportName": "ExportDataModal",
    "family": "data-transfer",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "API_TEST"
    ],
    "tags": [
      "export",
      "fields",
      "download"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "assign-owner-modal",
    "exportName": "AssignOwnerModal",
    "family": "assignment",
    "category": "CRM",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "owner",
      "people",
      "assignment"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "change-status-modal",
    "exportName": "ChangeStatusModal",
    "family": "status-transition",
    "category": "WORKFLOW",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "status",
      "transition",
      "workflow"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "schedule-action-modal",
    "exportName": "ScheduleActionModal",
    "family": "schedule",
    "category": "WORKFLOW",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "schedule",
      "command",
      "future"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "permission-matrix-modal",
    "exportName": "PermissionMatrixModal",
    "family": "permissions",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN"
    ],
    "tags": [
      "permissions",
      "access-control",
      "matrix"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "payload-preview-modal",
    "exportName": "PayloadPreviewModal",
    "family": "technical-preview",
    "category": "ADMIN",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "API_TEST",
      "ADMIN"
    ],
    "tags": [
      "payload",
      "json",
      "debug"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "dependency-impact-modal",
    "exportName": "DependencyImpactModal",
    "family": "destructive",
    "category": "INFRASTRUCTURE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "dependency",
      "impact",
      "confirmation"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "duplicate-resource-modal",
    "exportName": "DuplicateResourceModal",
    "family": "copy",
    "category": "WORKFLOW",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "duplicate",
      "copy",
      "children"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "resource-provisioning-wizard",
    "exportName": "ResourceProvisioningWizard",
    "family": "create-workflow",
    "category": "INFRASTRUCTURE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "provision",
      "create",
      "polling"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "deployment-workflow-wizard",
    "exportName": "DeploymentWorkflowWizard",
    "family": "deployment-workflow",
    "category": "INFRASTRUCTURE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "deployment",
      "progress",
      "strategy"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "approval-workflow-wizard",
    "exportName": "ApprovalWorkflowWizard",
    "family": "approval-workflow",
    "category": "WORKFLOW",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "approval",
      "review",
      "decision"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "publish-workflow-wizard",
    "exportName": "PublishWorkflowWizard",
    "family": "publish-workflow",
    "category": "CONTENT",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "publish",
      "schedule",
      "content"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "data-import-wizard",
    "exportName": "DataImportWizard",
    "family": "data-workflow",
    "category": "ADMIN",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "API_TEST"
    ],
    "tags": [
      "import",
      "validate",
      "commit"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "user-onboarding-wizard",
    "exportName": "UserOnboardingWizard",
    "family": "onboarding-workflow",
    "category": "CRM",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.overlay"
    ],
    "preferredPurposes": [
      "PRODUCT_LIKE",
      "ADMIN"
    ],
    "tags": [
      "user",
      "invite",
      "role"
    ],
    "states": [
      "IDLE",
      "LOADING",
      "EMPTY",
      "ERROR",
      "SUCCESS"
    ]
  }
] as const satisfies readonly BlueprintPartDescriptor[];

export type BlueprintPartId = (typeof BLUEPRINT_PARTS)[number]["componentId"];

export function blueprintPartsByCategory(category: BlueprintPartDescriptor["category"]) {
  return BLUEPRINT_PARTS.filter((part) => part.category === category);
}

export function blueprintPartsByPurpose(purpose: "ADMIN" | "PRODUCT_LIKE" | "API_TEST") {
  return BLUEPRINT_PARTS.filter((part) => (part.preferredPurposes as readonly string[]).includes(purpose));
}
