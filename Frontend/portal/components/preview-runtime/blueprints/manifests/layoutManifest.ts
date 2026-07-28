export const BLUEPRINT_LAYOUTS = [
  { id: "admin-workspace-layout", slots: ["page.header", "page.toolbar", "page.main", "page.aside", "page.footer", "page.overlay"], categories: ["ADMIN", "SETTINGS"] },
  { id: "analytics-dashboard-layout", slots: ["page.header", "page.summary", "page.main", "page.aside", "page.secondary"], categories: ["ANALYTICS", "OBSERVABILITY"] },
  { id: "commerce-catalog-layout", slots: ["page.header", "page.toolbar", "page.main", "page.aside", "page.overlay"], categories: ["COMMERCE"] },
  { id: "content-studio-layout", slots: ["page.header", "page.toolbar", "page.main", "page.aside", "page.overlay"], categories: ["CONTENT"] },
  { id: "master-detail-layout", slots: ["page.header", "page.toolbar", "page.main", "page.aside", "page.overlay"], categories: ["ADMIN", "CRM", "PROJECT"] },
  { id: "operations-cockpit-layout", slots: ["page.header", "page.summary", "page.main", "page.aside", "page.secondary", "page.actions"], categories: ["INFRASTRUCTURE", "OBSERVABILITY"] },
  { id: "settings-workbench-layout", slots: ["page.header", "page.main", "page.aside", "page.actions"], categories: ["SETTINGS", "ADMIN"] },
  { id: "workflow-stage-layout", slots: ["page.header", "page.main", "page.aside", "page.footer", "page.overlay"], categories: ["WORKFLOW"] },
] as const;
