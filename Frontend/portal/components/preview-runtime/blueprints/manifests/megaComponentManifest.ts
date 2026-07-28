import type { BlueprintPartDescriptor } from "../core";

export const MEGA_BLUEPRINT_PARTS = [
  {
    "componentId": "security-command-layout",
    "exportName": "SecurityCommandLayout",
    "family": "domain-layout",
    "category": "SECURITY",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "soc",
      "command"
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
    "componentId": "support-console-layout",
    "exportName": "SupportConsoleLayout",
    "family": "domain-layout",
    "category": "SUPPORT",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "tickets",
      "sla"
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
    "componentId": "finance-ledger-layout",
    "exportName": "FinanceLedgerLayout",
    "family": "domain-layout",
    "category": "FINANCE",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "ledger",
      "reconciliation"
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
    "componentId": "logistics-dispatch-layout",
    "exportName": "LogisticsDispatchLayout",
    "family": "domain-layout",
    "category": "LOGISTICS",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "dispatch",
      "map"
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
    "componentId": "inventory-warehouse-layout",
    "exportName": "InventoryWarehouseLayout",
    "family": "domain-layout",
    "category": "INVENTORY",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "warehouse",
      "stock"
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
    "componentId": "booking-planner-layout",
    "exportName": "BookingPlannerLayout",
    "family": "domain-layout",
    "category": "BOOKING",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "calendar",
      "capacity"
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
    "componentId": "event-operations-layout",
    "exportName": "EventOperationsLayout",
    "family": "domain-layout",
    "category": "EVENTS",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "attendance",
      "venue"
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
    "componentId": "learning-portal-layout",
    "exportName": "LearningPortalLayout",
    "family": "domain-layout",
    "category": "EDUCATION",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "courses",
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
    "componentId": "hr-people-ops-layout",
    "exportName": "HrPeopleOpsLayout",
    "family": "domain-layout",
    "category": "HR",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "people",
      "workforce"
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
    "componentId": "developer-platform-layout",
    "exportName": "DeveloperPlatformLayout",
    "family": "domain-layout",
    "category": "DEVELOPER",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "api",
      "platform"
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
    "componentId": "ai-studio-layout",
    "exportName": "AiStudioLayout",
    "family": "domain-layout",
    "category": "AI",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "models",
      "prompts"
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
    "componentId": "iot-control-layout",
    "exportName": "IoTControlLayout",
    "family": "domain-layout",
    "category": "IOT",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "devices",
      "telemetry"
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
    "componentId": "real-estate-portfolio-layout",
    "exportName": "RealEstatePortfolioLayout",
    "family": "domain-layout",
    "category": "REAL_ESTATE",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real-estate",
      "property",
      "tenant"
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
    "componentId": "knowledge-base-layout",
    "exportName": "KnowledgeBaseLayout",
    "family": "domain-layout",
    "category": "KNOWLEDGE",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "knowledge",
      "docs",
      "articles"
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
    "componentId": "social-community-layout",
    "exportName": "SocialCommunityLayout",
    "family": "domain-layout",
    "category": "COMMUNITY",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "social",
      "moderation"
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
    "componentId": "marketplace-operations-layout",
    "exportName": "MarketplaceOperationsLayout",
    "family": "domain-layout",
    "category": "MARKETPLACE",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "vendors",
      "payouts"
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
    "componentId": "billing-workspace-layout",
    "exportName": "BillingWorkspaceLayout",
    "family": "domain-layout",
    "category": "BILLING",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "subscriptions",
      "invoices"
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
    "componentId": "travel-planner-layout",
    "exportName": "TravelPlannerLayout",
    "family": "domain-layout",
    "category": "TRAVEL",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "travel",
      "itinerary",
      "booking"
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
    "componentId": "legal-case-layout",
    "exportName": "LegalCaseLayout",
    "family": "domain-layout",
    "category": "LEGAL",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "case",
      "documents"
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
    "componentId": "media-production-layout",
    "exportName": "MediaProductionLayout",
    "family": "domain-layout",
    "category": "MEDIA",
    "kind": "LAYOUT",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "assets",
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
    "componentId": "security-threat-dashboard",
    "exportName": "SecurityThreatDashboard",
    "family": "domain-dashboard",
    "category": "SECURITY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "threats",
      "soc"
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
    "componentId": "soc-overview-dashboard",
    "exportName": "SocOverviewDashboard",
    "family": "domain-dashboard",
    "category": "SECURITY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "soc",
      "terminal"
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
    "componentId": "support-sla-dashboard",
    "exportName": "SupportSlaDashboard",
    "family": "domain-dashboard",
    "category": "SUPPORT",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "sla",
      "tickets"
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
    "componentId": "customer-success-dashboard",
    "exportName": "CustomerSuccessDashboard",
    "family": "domain-dashboard",
    "category": "CRM",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "crm",
      "customer-success",
      "health"
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
    "componentId": "finance-cashflow-dashboard",
    "exportName": "FinanceCashflowDashboard",
    "family": "domain-dashboard",
    "category": "FINANCE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "cashflow",
      "forecast"
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
    "componentId": "billing-revenue-dashboard",
    "exportName": "BillingRevenueDashboard",
    "family": "domain-dashboard",
    "category": "BILLING",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "revenue",
      "subscription"
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
    "componentId": "inventory-turnover-dashboard",
    "exportName": "InventoryTurnoverDashboard",
    "family": "domain-dashboard",
    "category": "INVENTORY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "stock",
      "turnover"
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
    "componentId": "warehouse-capacity-dashboard",
    "exportName": "WarehouseCapacityDashboard",
    "family": "domain-dashboard",
    "category": "INVENTORY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "warehouse",
      "capacity",
      "bins"
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
    "componentId": "logistics-fleet-dashboard",
    "exportName": "LogisticsFleetDashboard",
    "family": "domain-dashboard",
    "category": "LOGISTICS",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "fleet",
      "routes"
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
    "componentId": "delivery-performance-dashboard",
    "exportName": "DeliveryPerformanceDashboard",
    "family": "domain-dashboard",
    "category": "LOGISTICS",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "delivery",
      "performance",
      "regional"
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
    "componentId": "booking-occupancy-dashboard",
    "exportName": "BookingOccupancyDashboard",
    "family": "domain-dashboard",
    "category": "BOOKING",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "occupancy",
      "availability"
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
    "componentId": "event-attendance-dashboard",
    "exportName": "EventAttendanceDashboard",
    "family": "domain-dashboard",
    "category": "EVENTS",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "attendance",
      "checkin"
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
    "componentId": "learning-progress-dashboard",
    "exportName": "LearningProgressDashboard",
    "family": "domain-dashboard",
    "category": "EDUCATION",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "learning",
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
    "componentId": "cohort-performance-dashboard",
    "exportName": "CohortPerformanceDashboard",
    "family": "domain-dashboard",
    "category": "EDUCATION",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "cohort",
      "assessment"
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
    "componentId": "hr-workforce-dashboard",
    "exportName": "HrWorkforceDashboard",
    "family": "domain-dashboard",
    "category": "HR",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "workforce",
      "retention"
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
    "componentId": "recruiting-pipeline-dashboard",
    "exportName": "RecruitingPipelineDashboard",
    "family": "domain-dashboard",
    "category": "HR",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "recruiting",
      "candidates"
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
    "componentId": "developer-usage-dashboard",
    "exportName": "DeveloperUsageDashboard",
    "family": "domain-dashboard",
    "category": "DEVELOPER",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "api",
      "usage"
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
    "componentId": "api-reliability-dashboard",
    "exportName": "ApiReliabilityDashboard",
    "family": "domain-dashboard",
    "category": "DEVELOPER",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "api",
      "reliability",
      "latency"
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
    "componentId": "ai-model-ops-dashboard",
    "exportName": "AiModelOpsDashboard",
    "family": "domain-dashboard",
    "category": "AI",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "models",
      "operations"
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
    "componentId": "prompt-analytics-dashboard",
    "exportName": "PromptAnalyticsDashboard",
    "family": "domain-dashboard",
    "category": "AI",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "prompts",
      "analytics"
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
    "componentId": "iot-device-fleet-dashboard",
    "exportName": "IoTDeviceFleetDashboard",
    "family": "domain-dashboard",
    "category": "IOT",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "devices",
      "fleet"
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
    "componentId": "energy-usage-dashboard",
    "exportName": "EnergyUsageDashboard",
    "family": "domain-dashboard",
    "category": "IOT",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "energy",
      "telemetry"
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
    "componentId": "real-estate-portfolio-dashboard",
    "exportName": "RealEstatePortfolioDashboard",
    "family": "domain-dashboard",
    "category": "REAL_ESTATE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real-estate",
      "portfolio",
      "occupancy"
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
    "componentId": "property-occupancy-dashboard",
    "exportName": "PropertyOccupancyDashboard",
    "family": "domain-dashboard",
    "category": "REAL_ESTATE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "property",
      "occupancy",
      "leases"
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
    "componentId": "community-engagement-dashboard",
    "exportName": "CommunityEngagementDashboard",
    "family": "domain-dashboard",
    "category": "COMMUNITY",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "engagement",
      "members"
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
    "componentId": "marketplace-liquidity-dashboard",
    "exportName": "MarketplaceLiquidityDashboard",
    "family": "domain-dashboard",
    "category": "MARKETPLACE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "liquidity",
      "conversion"
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
    "componentId": "travel-operations-dashboard",
    "exportName": "TravelOperationsDashboard",
    "family": "domain-dashboard",
    "category": "TRAVEL",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "travel",
      "operations",
      "trips"
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
    "componentId": "legal-matter-dashboard",
    "exportName": "LegalMatterDashboard",
    "family": "domain-dashboard",
    "category": "LEGAL",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "matters",
      "risk"
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
    "componentId": "media-pipeline-dashboard",
    "exportName": "MediaPipelineDashboard",
    "family": "domain-dashboard",
    "category": "MEDIA",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "production",
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
    "componentId": "knowledge-health-dashboard",
    "exportName": "KnowledgeHealthDashboard",
    "family": "domain-dashboard",
    "category": "KNOWLEDGE",
    "kind": "DASHBOARD",
    "acceptedSurfaces": [
      "page.content",
      "page.main"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "knowledge",
      "docs",
      "freshness"
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
    "componentId": "threat-event-stream",
    "exportName": "ThreatEventStream",
    "family": "domain-collection",
    "category": "SECURITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "events",
      "timeline"
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
    "componentId": "vulnerability-matrix",
    "exportName": "VulnerabilityMatrix",
    "family": "domain-collection",
    "category": "SECURITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "vulnerability",
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
    "componentId": "support-ticket-inbox",
    "exportName": "SupportTicketInbox",
    "family": "domain-collection",
    "category": "SUPPORT",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "tickets",
      "inbox"
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
    "componentId": "customer-health-board",
    "exportName": "CustomerHealthBoard",
    "family": "domain-collection",
    "category": "CRM",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "crm",
      "health",
      "board"
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
    "componentId": "transaction-ledger",
    "exportName": "TransactionLedger",
    "family": "domain-collection",
    "category": "FINANCE",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "transactions",
      "ledger"
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
    "componentId": "invoice-collection",
    "exportName": "InvoiceCollection",
    "family": "domain-collection",
    "category": "BILLING",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "invoices",
      "collections"
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
    "componentId": "inventory-sku-matrix",
    "exportName": "InventorySkuMatrix",
    "family": "domain-collection",
    "category": "INVENTORY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "sku",
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
    "componentId": "warehouse-bin-explorer",
    "exportName": "WarehouseBinExplorer",
    "family": "domain-collection",
    "category": "INVENTORY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "warehouse",
      "bins",
      "tree"
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
    "componentId": "shipment-tracking-board",
    "exportName": "ShipmentTrackingBoard",
    "family": "domain-collection",
    "category": "LOGISTICS",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "shipment",
      "board"
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
    "componentId": "route-stop-timeline",
    "exportName": "RouteStopTimeline",
    "family": "domain-collection",
    "category": "LOGISTICS",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "route",
      "timeline"
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
    "componentId": "reservation-calendar",
    "exportName": "ReservationCalendar",
    "family": "domain-collection",
    "category": "BOOKING",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "calendar",
      "reservation"
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
    "componentId": "venue-seat-map",
    "exportName": "VenueSeatMap",
    "family": "domain-collection",
    "category": "EVENTS",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "venue",
      "seat"
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
    "componentId": "course-catalog-grid",
    "exportName": "CourseCatalogGrid",
    "family": "domain-collection",
    "category": "EDUCATION",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "courses",
      "catalog"
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
    "componentId": "learner-roster",
    "exportName": "LearnerRoster",
    "family": "domain-collection",
    "category": "EDUCATION",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "learners",
      "roster"
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
    "componentId": "employee-directory-grid",
    "exportName": "EmployeeDirectoryGrid",
    "family": "domain-collection",
    "category": "HR",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "employees",
      "directory"
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
    "componentId": "candidate-pipeline",
    "exportName": "CandidatePipeline",
    "family": "domain-collection",
    "category": "HR",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "candidates",
      "pipeline"
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
    "componentId": "api-endpoint-catalog",
    "exportName": "ApiEndpointCatalog",
    "family": "domain-collection",
    "category": "DEVELOPER",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "api",
      "catalog"
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
    "componentId": "deployment-environment-matrix",
    "exportName": "DeploymentEnvironmentMatrix",
    "family": "domain-collection",
    "category": "DEVELOPER",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "deployment",
      "environment"
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
    "componentId": "model-registry-collection",
    "exportName": "ModelRegistryCollection",
    "family": "domain-collection",
    "category": "AI",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "models",
      "registry"
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
    "componentId": "prompt-library-grid",
    "exportName": "PromptLibraryGrid",
    "family": "domain-collection",
    "category": "AI",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "prompts",
      "library"
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
    "componentId": "device-topology-list",
    "exportName": "DeviceTopologyList",
    "family": "domain-collection",
    "category": "IOT",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "devices",
      "topology"
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
    "componentId": "sensor-reading-table",
    "exportName": "SensorReadingTable",
    "family": "domain-collection",
    "category": "IOT",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "telemetry",
      "sensors"
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
    "componentId": "property-listing-grid",
    "exportName": "PropertyListingGrid",
    "family": "domain-collection",
    "category": "REAL_ESTATE",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real-estate",
      "listings",
      "gallery"
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
    "componentId": "tenant-directory",
    "exportName": "TenantDirectory",
    "family": "domain-collection",
    "category": "REAL_ESTATE",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real-estate",
      "tenants",
      "directory"
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
    "componentId": "community-feed",
    "exportName": "CommunityFeed",
    "family": "domain-collection",
    "category": "COMMUNITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "feed",
      "social"
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
    "componentId": "moderation-queue",
    "exportName": "ModerationQueue",
    "family": "domain-collection",
    "category": "COMMUNITY",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "moderation",
      "queue"
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
    "componentId": "vendor-marketplace-grid",
    "exportName": "VendorMarketplaceGrid",
    "family": "domain-collection",
    "category": "MARKETPLACE",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "vendors",
      "catalog"
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
    "componentId": "trip-itinerary-collection",
    "exportName": "TripItineraryCollection",
    "family": "domain-collection",
    "category": "TRAVEL",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "travel",
      "itinerary",
      "timeline"
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
    "componentId": "legal-case-docket",
    "exportName": "LegalCaseDocket",
    "family": "domain-collection",
    "category": "LEGAL",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "docket",
      "timeline"
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
    "componentId": "asset-production-board",
    "exportName": "AssetProductionBoard",
    "family": "domain-collection",
    "category": "MEDIA",
    "kind": "COLLECTION",
    "acceptedSurfaces": [
      "page.main",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "assets",
      "production"
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
    "componentId": "threat-incident-detail",
    "exportName": "ThreatIncidentDetail",
    "family": "domain-detail",
    "category": "SECURITY",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "casefile",
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
    "componentId": "vulnerability-detail",
    "exportName": "VulnerabilityDetail",
    "family": "domain-detail",
    "category": "SECURITY",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "technical",
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
    "componentId": "support-ticket-detail",
    "exportName": "SupportTicketDetail",
    "family": "domain-detail",
    "category": "SUPPORT",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "timeline",
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
    "componentId": "customer-success-detail",
    "exportName": "CustomerSuccessDetail",
    "family": "domain-detail",
    "category": "CRM",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "crm",
      "profile",
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
    "componentId": "transaction-detail",
    "exportName": "TransactionDetail",
    "family": "domain-detail",
    "category": "FINANCE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "document",
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
    "componentId": "invoice-detail",
    "exportName": "InvoiceDetail",
    "family": "domain-detail",
    "category": "BILLING",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "commerce",
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
    "componentId": "inventory-item-detail",
    "exportName": "InventoryItemDetail",
    "family": "domain-detail",
    "category": "INVENTORY",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "split",
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
    "componentId": "shipment-detail",
    "exportName": "ShipmentDetail",
    "family": "domain-detail",
    "category": "LOGISTICS",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "timeline",
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
    "componentId": "reservation-detail",
    "exportName": "ReservationDetail",
    "family": "domain-detail",
    "category": "BOOKING",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "hero",
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
    "componentId": "event-detail",
    "exportName": "EventDetail",
    "family": "domain-detail",
    "category": "EVENTS",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "hero",
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
    "componentId": "course-detail",
    "exportName": "CourseDetail",
    "family": "domain-detail",
    "category": "EDUCATION",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "document",
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
    "componentId": "learner-detail",
    "exportName": "LearnerDetail",
    "family": "domain-detail",
    "category": "EDUCATION",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "profile",
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
    "componentId": "employee-profile-detail",
    "exportName": "EmployeeProfileDetail",
    "family": "domain-detail",
    "category": "HR",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "profile",
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
    "componentId": "candidate-detail",
    "exportName": "CandidateDetail",
    "family": "domain-detail",
    "category": "HR",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "casefile",
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
    "componentId": "api-product-detail",
    "exportName": "ApiProductDetail",
    "family": "domain-detail",
    "category": "DEVELOPER",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "technical",
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
    "componentId": "deployment-detail",
    "exportName": "DeploymentDetail",
    "family": "domain-detail",
    "category": "DEVELOPER",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "technical",
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
    "componentId": "model-detail",
    "exportName": "ModelDetail",
    "family": "domain-detail",
    "category": "AI",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "technical",
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
    "componentId": "prompt-detail",
    "exportName": "PromptDetail",
    "family": "domain-detail",
    "category": "AI",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "document",
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
    "componentId": "device-detail",
    "exportName": "DeviceDetail",
    "family": "domain-detail",
    "category": "IOT",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "technical",
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
    "componentId": "property-detail",
    "exportName": "PropertyDetail",
    "family": "domain-detail",
    "category": "REAL_ESTATE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real_estate",
      "commerce",
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
    "componentId": "community-member-detail",
    "exportName": "CommunityMemberDetail",
    "family": "domain-detail",
    "category": "COMMUNITY",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "profile",
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
    "componentId": "vendor-detail",
    "exportName": "VendorDetail",
    "family": "domain-detail",
    "category": "MARKETPLACE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "commerce",
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
    "componentId": "trip-detail",
    "exportName": "TripDetail",
    "family": "domain-detail",
    "category": "TRAVEL",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "travel",
      "timeline",
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
    "componentId": "legal-matter-detail",
    "exportName": "LegalMatterDetail",
    "family": "domain-detail",
    "category": "LEGAL",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "casefile",
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
    "componentId": "media-asset-detail",
    "exportName": "MediaAssetDetail",
    "family": "domain-detail",
    "category": "MEDIA",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "commerce",
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
    "componentId": "knowledge-article-detail",
    "exportName": "KnowledgeArticleDetail",
    "family": "domain-detail",
    "category": "KNOWLEDGE",
    "kind": "DETAIL",
    "acceptedSurfaces": [
      "page.main",
      "page.aside",
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "knowledge",
      "document",
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
    "componentId": "acknowledge-alert-modal",
    "exportName": "AcknowledgeAlertModal",
    "family": "domain-modal",
    "category": "SECURITY",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "review",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "escalate-incident-modal",
    "exportName": "EscalateIncidentModal",
    "family": "domain-modal",
    "category": "SECURITY",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "merge-tickets-modal",
    "exportName": "MergeTicketsModal",
    "family": "domain-modal",
    "category": "SUPPORT",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "impact",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "send-reply-modal",
    "exportName": "SendReplyModal",
    "family": "domain-modal",
    "category": "SUPPORT",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "issue-refund-modal",
    "exportName": "IssueRefundModal",
    "family": "domain-modal",
    "category": "FINANCE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "capture-payment-modal",
    "exportName": "CapturePaymentModal",
    "family": "domain-modal",
    "category": "FINANCE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "confirm",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "adjust-inventory-modal",
    "exportName": "AdjustInventoryModal",
    "family": "domain-modal",
    "category": "INVENTORY",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "transfer-stock-modal",
    "exportName": "TransferStockModal",
    "family": "domain-modal",
    "category": "INVENTORY",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "reassign-shipment-modal",
    "exportName": "ReassignShipmentModal",
    "family": "domain-modal",
    "category": "LOGISTICS",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "picker",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "delivery-exception-modal",
    "exportName": "DeliveryExceptionModal",
    "family": "domain-modal",
    "category": "LOGISTICS",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "impact",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "reschedule-booking-modal",
    "exportName": "RescheduleBookingModal",
    "family": "domain-modal",
    "category": "BOOKING",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "schedule",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "seat-assignment-modal",
    "exportName": "SeatAssignmentModal",
    "family": "domain-modal",
    "category": "EVENTS",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "picker",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "enroll-learner-modal",
    "exportName": "EnrollLearnerModal",
    "family": "domain-modal",
    "category": "EDUCATION",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "grade-submission-modal",
    "exportName": "GradeSubmissionModal",
    "family": "domain-modal",
    "category": "EDUCATION",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "review",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "time-off-request-modal",
    "exportName": "TimeOffRequestModal",
    "family": "domain-modal",
    "category": "HR",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "compensation-change-modal",
    "exportName": "CompensationChangeModal",
    "family": "domain-modal",
    "category": "HR",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "rotate-api-key-modal",
    "exportName": "RotateApiKeyModal",
    "family": "domain-modal",
    "category": "DEVELOPER",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "promote-deployment-modal",
    "exportName": "PromoteDeploymentModal",
    "family": "domain-modal",
    "category": "DEVELOPER",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "review",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "model-evaluation-modal",
    "exportName": "ModelEvaluationModal",
    "family": "domain-modal",
    "category": "AI",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "prompt-test-modal",
    "exportName": "PromptTestModal",
    "family": "domain-modal",
    "category": "AI",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "command",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "device-command-modal",
    "exportName": "DeviceCommandModal",
    "family": "domain-modal",
    "category": "IOT",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "command",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "firmware-update-modal",
    "exportName": "FirmwareUpdateModal",
    "family": "domain-modal",
    "category": "IOT",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "schedule",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "property-inquiry-modal",
    "exportName": "PropertyInquiryModal",
    "family": "domain-modal",
    "category": "REAL_ESTATE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real_estate",
      "form",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "lease-renewal-modal",
    "exportName": "LeaseRenewalModal",
    "family": "domain-modal",
    "category": "REAL_ESTATE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real_estate",
      "review",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "moderate-content-modal",
    "exportName": "ModerateContentModal",
    "family": "domain-modal",
    "category": "COMMUNITY",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "community",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "vendor-payout-modal",
    "exportName": "VendorPayoutModal",
    "family": "domain-modal",
    "category": "MARKETPLACE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "review",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "trip-change-modal",
    "exportName": "TripChangeModal",
    "family": "domain-modal",
    "category": "TRAVEL",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "travel",
      "impact",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "legal-hold-modal",
    "exportName": "LegalHoldModal",
    "family": "domain-modal",
    "category": "LEGAL",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "danger",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "publish-asset-modal",
    "exportName": "PublishAssetModal",
    "family": "domain-modal",
    "category": "MEDIA",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "schedule",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "knowledge-merge-modal",
    "exportName": "KnowledgeMergeModal",
    "family": "domain-modal",
    "category": "KNOWLEDGE",
    "kind": "MODAL",
    "acceptedSurfaces": [
      "page.overlay",
      "modal.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "knowledge",
      "impact",
      "modal"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "incident-response-wizard",
    "exportName": "IncidentResponseWizard",
    "family": "domain-workflow",
    "category": "SECURITY",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "incident",
      "response"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "vulnerability-remediation-wizard",
    "exportName": "VulnerabilityRemediationWizard",
    "family": "domain-workflow",
    "category": "SECURITY",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "vulnerability",
      "remediation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "ticket-escalation-wizard",
    "exportName": "TicketEscalationWizard",
    "family": "domain-workflow",
    "category": "SUPPORT",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "ticket",
      "escalation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "customer-renewal-wizard",
    "exportName": "CustomerRenewalWizard",
    "family": "domain-workflow",
    "category": "CRM",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "crm",
      "renewal",
      "customer"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "payment-reconciliation-wizard",
    "exportName": "PaymentReconciliationWizard",
    "family": "domain-workflow",
    "category": "FINANCE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "finance",
      "payment",
      "reconciliation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "subscription-upgrade-wizard",
    "exportName": "SubscriptionUpgradeWizard",
    "family": "domain-workflow",
    "category": "BILLING",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "subscription",
      "upgrade"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "stock-replenishment-wizard",
    "exportName": "StockReplenishmentWizard",
    "family": "domain-workflow",
    "category": "INVENTORY",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "inventory",
      "stock",
      "replenishment"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "shipment-exception-wizard",
    "exportName": "ShipmentExceptionWizard",
    "family": "domain-workflow",
    "category": "LOGISTICS",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "logistics",
      "shipment",
      "exception"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "reservation-setup-wizard",
    "exportName": "ReservationSetupWizard",
    "family": "domain-workflow",
    "category": "BOOKING",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "booking",
      "reservation",
      "setup"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "event-launch-wizard",
    "exportName": "EventLaunchWizard",
    "family": "domain-workflow",
    "category": "EVENTS",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "events",
      "launch",
      "venue"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "course-publishing-wizard",
    "exportName": "CoursePublishingWizard",
    "family": "domain-workflow",
    "category": "EDUCATION",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "education",
      "course",
      "publishing"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "employee-onboarding-wizard",
    "exportName": "EmployeeOnboardingWizard",
    "family": "domain-workflow",
    "category": "HR",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "employee",
      "onboarding"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "candidate-hiring-wizard",
    "exportName": "CandidateHiringWizard",
    "family": "domain-workflow",
    "category": "HR",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "hr",
      "candidate",
      "hiring"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "api-product-launch-wizard",
    "exportName": "ApiProductLaunchWizard",
    "family": "domain-workflow",
    "category": "DEVELOPER",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "api",
      "launch"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "release-promotion-wizard",
    "exportName": "ReleasePromotionWizard",
    "family": "domain-workflow",
    "category": "DEVELOPER",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "release",
      "deployment"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "model-deployment-wizard",
    "exportName": "ModelDeploymentWizard",
    "family": "domain-workflow",
    "category": "AI",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "ai",
      "model",
      "deployment"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "device-provisioning-wizard",
    "exportName": "DeviceProvisioningWizard",
    "family": "domain-workflow",
    "category": "IOT",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "iot",
      "device",
      "provisioning"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "property-listing-wizard",
    "exportName": "PropertyListingWizard",
    "family": "domain-workflow",
    "category": "REAL_ESTATE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "real-estate",
      "property",
      "listing"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "vendor-onboarding-wizard",
    "exportName": "VendorOnboardingWizard",
    "family": "domain-workflow",
    "category": "MARKETPLACE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "marketplace",
      "vendor",
      "onboarding"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "legal-review-wizard",
    "exportName": "LegalReviewWizard",
    "family": "domain-workflow",
    "category": "LEGAL",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "legal",
      "review",
      "approval"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "media-publishing-wizard",
    "exportName": "MediaPublishingWizard",
    "family": "domain-workflow",
    "category": "MEDIA",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "asset",
      "publishing"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "knowledge-approval-wizard",
    "exportName": "KnowledgeApprovalWizard",
    "family": "domain-workflow",
    "category": "KNOWLEDGE",
    "kind": "WORKFLOW",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "page.overlay"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "knowledge",
      "article",
      "approval"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "SUBMITTING",
      "COMPLETED",
      "ERROR"
    ]
  },
  {
    "componentId": "dynamic-schema-form",
    "exportName": "DynamicSchemaForm",
    "family": "domain-form",
    "category": "SETTINGS",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "schema",
      "dynamic",
      "form"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "sectioned-settings-form",
    "exportName": "SectionedSettingsForm",
    "family": "domain-form",
    "category": "SETTINGS",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "settings",
      "sectioned",
      "configuration"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "inline-quick-edit-form",
    "exportName": "InlineQuickEditForm",
    "family": "domain-form",
    "category": "ADMIN",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "admin",
      "inline",
      "edit"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "query-builder-form",
    "exportName": "QueryBuilderForm",
    "family": "domain-form",
    "category": "ANALYTICS",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "query",
      "builder",
      "analytics"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "filter-rule-builder",
    "exportName": "FilterRuleBuilder",
    "family": "domain-form",
    "category": "ADMIN",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "filters",
      "rules",
      "builder"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "pricing-plan-form",
    "exportName": "PricingPlanForm",
    "family": "domain-form",
    "category": "BILLING",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "billing",
      "pricing",
      "plan"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "checkout-address-form",
    "exportName": "CheckoutAddressForm",
    "family": "domain-form",
    "category": "COMMERCE",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "commerce",
      "checkout",
      "address"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "support-reply-composer",
    "exportName": "SupportReplyComposer",
    "family": "domain-form",
    "category": "SUPPORT",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "support",
      "reply",
      "composer"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "content-editor-form",
    "exportName": "ContentEditorForm",
    "family": "domain-form",
    "category": "CONTENT",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "content",
      "editor",
      "publishing"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "metadata-editor-form",
    "exportName": "MetadataEditorForm",
    "family": "domain-form",
    "category": "MEDIA",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "media",
      "metadata",
      "editor"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "api-request-builder-form",
    "exportName": "ApiRequestBuilderForm",
    "family": "domain-form",
    "category": "DEVELOPER",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "api",
      "request"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "secret-reference-form",
    "exportName": "SecretReferenceForm",
    "family": "domain-form",
    "category": "DEVELOPER",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "developer",
      "secret",
      "reference"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "schedule-rule-form",
    "exportName": "ScheduleRuleForm",
    "family": "domain-form",
    "category": "WORKFLOW",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "workflow",
      "schedule",
      "recurrence"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "permission-policy-form",
    "exportName": "PermissionPolicyForm",
    "family": "domain-form",
    "category": "SECURITY",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "security",
      "permission",
      "policy"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "notification-preference-form",
    "exportName": "NotificationPreferenceForm",
    "family": "domain-form",
    "category": "SETTINGS",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "settings",
      "notifications",
      "preferences"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "survey-builder-form",
    "exportName": "SurveyBuilderForm",
    "family": "domain-form",
    "category": "CONTENT",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "survey",
      "builder",
      "content"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "localization-form",
    "exportName": "LocalizationForm",
    "family": "domain-form",
    "category": "CONTENT",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "content",
      "localization",
      "translation"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "theme-configurator-form",
    "exportName": "ThemeConfiguratorForm",
    "family": "domain-form",
    "category": "THEME",
    "kind": "FORM",
    "acceptedSurfaces": [
      "page.main",
      "page.content",
      "modal.body",
      "drawer.body"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE"
    ],
    "tags": [
      "theme",
      "design",
      "tokens"
    ],
    "states": [
      "IDLE",
      "SUBMITTING",
      "ERROR",
      "SUCCESS"
    ]
  },
  {
    "componentId": "global-command-bar",
    "exportName": "GlobalCommandBar",
    "family": "action-pattern",
    "category": "DEVELOPER",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "developer",
      "commandbar",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "dense-admin-toolbar",
    "exportName": "DenseAdminToolbar",
    "family": "action-pattern",
    "category": "ADMIN",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "toolbar",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "product-hero-actions",
    "exportName": "ProductHeroActions",
    "family": "action-pattern",
    "category": "COMMERCE",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "commerce",
      "toolbar",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "resource-quick-actions",
    "exportName": "ResourceQuickActions",
    "family": "action-pattern",
    "category": "INFRASTRUCTURE",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "infrastructure",
      "chips",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "bulk-selection-toolbar",
    "exportName": "BulkSelectionToolbar",
    "family": "action-pattern",
    "category": "ADMIN",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "bulk",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "inline-row-actions",
    "exportName": "InlineRowActions",
    "family": "action-pattern",
    "category": "ADMIN",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "segmented",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "split-button-actions",
    "exportName": "SplitButtonActions",
    "family": "action-pattern",
    "category": "WORKFLOW",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "segmented",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "floating-create-action",
    "exportName": "FloatingCreateAction",
    "family": "action-pattern",
    "category": "COMMERCE",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "product",
      "floating",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "status-transition-bar",
    "exportName": "StatusTransitionBar",
    "family": "action-pattern",
    "category": "WORKFLOW",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "toolbar",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "review-decision-bar",
    "exportName": "ReviewDecisionBar",
    "family": "action-pattern",
    "category": "LEGAL",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "legal",
      "review",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "pagination-toolbar",
    "exportName": "PaginationToolbar",
    "family": "action-pattern",
    "category": "ANALYTICS",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "analytics",
      "segmented",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "view-mode-switcher",
    "exportName": "ViewModeSwitcher",
    "family": "action-pattern",
    "category": "SETTINGS",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "settings",
      "segmented",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "filter-chip-bar",
    "exportName": "FilterChipBar",
    "family": "action-pattern",
    "category": "ANALYTICS",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "analytics",
      "chips",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "saved-view-bar",
    "exportName": "SavedViewBar",
    "family": "action-pattern",
    "category": "ADMIN",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "chips",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "share-export-toolbar",
    "exportName": "ShareExportToolbar",
    "family": "action-pattern",
    "category": "CONTENT",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "content",
      "toolbar",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "danger-zone-actions",
    "exportName": "DangerZoneActions",
    "family": "action-pattern",
    "category": "SECURITY",
    "kind": "ACTION",
    "acceptedSurfaces": [
      "page.actions",
      "page.toolbar",
      "table.toolbar",
      "table.row-actions"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "security",
      "danger",
      "actions"
    ],
    "states": [
      "IDLE",
      "DISABLED",
      "SUBMITTING"
    ]
  },
  {
    "componentId": "icon-rail-navigation",
    "exportName": "IconRailNavigation",
    "family": "navigation-pattern",
    "category": "ADMIN",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "rail",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "collapsible-sidebar-navigation",
    "exportName": "CollapsibleSidebarNavigation",
    "family": "navigation-pattern",
    "category": "ADMIN",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "sidebar",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "workspace-top-navigation",
    "exportName": "WorkspaceTopNavigation",
    "family": "navigation-pattern",
    "category": "COMMERCE",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "product",
      "top",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "breadcrumb-trail",
    "exportName": "BreadcrumbTrail",
    "family": "navigation-pattern",
    "category": "CONTENT",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "content",
      "breadcrumbs",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "tabbed-section-navigation",
    "exportName": "TabbedSectionNavigation",
    "family": "navigation-pattern",
    "category": "SETTINGS",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "settings",
      "tabs",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "stepper-navigation",
    "exportName": "StepperNavigation",
    "family": "navigation-pattern",
    "category": "WORKFLOW",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "stepper",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "command-palette",
    "exportName": "CommandPalette",
    "family": "navigation-pattern",
    "category": "DEVELOPER",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "developer",
      "palette",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "mega-menu-navigation",
    "exportName": "MegaMenuNavigation",
    "family": "navigation-pattern",
    "category": "COMMERCE",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "commerce",
      "mega",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "context-switcher-navigation",
    "exportName": "ContextSwitcherNavigation",
    "family": "navigation-pattern",
    "category": "ADMIN",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "sidebar",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "mobile-bottom-navigation",
    "exportName": "MobileBottomNavigation",
    "family": "navigation-pattern",
    "category": "COMMERCE",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "product",
      "bottom",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "admin-tree-navigation",
    "exportName": "AdminTreeNavigation",
    "family": "navigation-pattern",
    "category": "ADMIN",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "tree",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "docs-navigation",
    "exportName": "DocsNavigation",
    "family": "navigation-pattern",
    "category": "KNOWLEDGE",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "knowledge",
      "tree",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "calendar-navigation",
    "exportName": "CalendarNavigation",
    "family": "navigation-pattern",
    "category": "BOOKING",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "booking",
      "tabs",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "product-subnav",
    "exportName": "ProductSubnav",
    "family": "navigation-pattern",
    "category": "COMMERCE",
    "kind": "NAVIGATION",
    "acceptedSurfaces": [
      "page.header",
      "page.toolbar",
      "page.aside"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "product",
      "top",
      "navigation"
    ],
    "states": [
      "IDLE",
      "ACTIVE",
      "DISABLED"
    ]
  },
  {
    "componentId": "illustrated-empty-state",
    "exportName": "IllustratedEmptyState",
    "family": "feedback-state",
    "category": "CONTENT",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "content",
      "empty",
      "feedback"
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
    "componentId": "permission-denied-state",
    "exportName": "PermissionDeniedState",
    "family": "feedback-state",
    "category": "SECURITY",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "security",
      "permission",
      "feedback"
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
    "componentId": "offline-state",
    "exportName": "OfflineState",
    "family": "feedback-state",
    "category": "OBSERVABILITY",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "observability",
      "offline",
      "feedback"
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
    "componentId": "maintenance-state",
    "exportName": "MaintenanceState",
    "family": "feedback-state",
    "category": "INFRASTRUCTURE",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "infrastructure",
      "maintenance",
      "feedback"
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
    "componentId": "rate-limit-state",
    "exportName": "RateLimitState",
    "family": "feedback-state",
    "category": "DEVELOPER",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "developer",
      "warning",
      "feedback"
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
    "componentId": "data-load-error-state",
    "exportName": "DataLoadErrorState",
    "family": "feedback-state",
    "category": "OBSERVABILITY",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "observability",
      "error",
      "feedback"
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
    "componentId": "search-no-results-state",
    "exportName": "SearchNoResultsState",
    "family": "feedback-state",
    "category": "ANALYTICS",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "analytics",
      "empty",
      "feedback"
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
    "componentId": "import-success-state",
    "exportName": "ImportSuccessState",
    "family": "feedback-state",
    "category": "WORKFLOW",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "success",
      "feedback"
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
    "componentId": "operation-failed-state",
    "exportName": "OperationFailedState",
    "family": "feedback-state",
    "category": "WORKFLOW",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "error",
      "feedback"
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
    "componentId": "skeleton-dashboard-state",
    "exportName": "SkeletonDashboardState",
    "family": "feedback-state",
    "category": "ANALYTICS",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "analytics",
      "loading",
      "feedback"
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
    "componentId": "table-loading-state",
    "exportName": "TableLoadingState",
    "family": "feedback-state",
    "category": "ADMIN",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "admin",
      "loading",
      "feedback"
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
    "componentId": "partial-data-warning",
    "exportName": "PartialDataWarning",
    "family": "feedback-state",
    "category": "OBSERVABILITY",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "observability",
      "warning",
      "feedback"
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
    "componentId": "unsaved-changes-banner",
    "exportName": "UnsavedChangesBanner",
    "family": "feedback-state",
    "category": "SETTINGS",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "settings",
      "warning",
      "feedback"
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
    "componentId": "first-run-onboarding-state",
    "exportName": "FirstRunOnboardingState",
    "family": "feedback-state",
    "category": "WORKFLOW",
    "kind": "FEEDBACK",
    "acceptedSurfaces": [
      "page.content",
      "page.main",
      "table.empty"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "workflow",
      "onboarding",
      "feedback"
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
    "componentId": "terminal-green-theme",
    "exportName": "TerminalGreenTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "terminal-green-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "terminal-green-theme"
  },
  {
    "componentId": "glass-aurora-theme",
    "exportName": "GlassAuroraTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "glass-aurora-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "glass-aurora-theme"
  },
  {
    "componentId": "editorial-paper-theme",
    "exportName": "EditorialPaperTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "editorial-paper-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "editorial-paper-theme"
  },
  {
    "componentId": "brutalist-mono-theme",
    "exportName": "BrutalistMonoTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "brutalist-mono-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "brutalist-mono-theme"
  },
  {
    "componentId": "enterprise-blue-theme",
    "exportName": "EnterpriseBlueTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "enterprise-blue-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "enterprise-blue-theme"
  },
  {
    "componentId": "warm-commerce-theme",
    "exportName": "WarmCommerceTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "warm-commerce-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "warm-commerce-theme"
  },
  {
    "componentId": "neon-cyber-theme",
    "exportName": "NeonCyberTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "neon-cyber-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "neon-cyber-theme"
  },
  {
    "componentId": "soft-pastel-theme",
    "exportName": "SoftPastelTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "soft-pastel-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "soft-pastel-theme"
  },
  {
    "componentId": "ocean-data-theme",
    "exportName": "OceanDataTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "ocean-data-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "ocean-data-theme"
  },
  {
    "componentId": "sunset-operations-theme",
    "exportName": "SunsetOperationsTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "sunset-operations-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "sunset-operations-theme"
  },
  {
    "componentId": "forest-saas-theme",
    "exportName": "ForestSaaSTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "forest-saas-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "forest-saas-theme"
  },
  {
    "componentId": "monochrome-pro-theme",
    "exportName": "MonochromeProTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "monochrome-pro-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "monochrome-pro-theme"
  },
  {
    "componentId": "lavender-creator-theme",
    "exportName": "LavenderCreatorTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "lavender-creator-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "lavender-creator-theme"
  },
  {
    "componentId": "crimson-security-theme",
    "exportName": "CrimsonSecurityTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "crimson-security-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "crimson-security-theme"
  },
  {
    "componentId": "sandstone-finance-theme",
    "exportName": "SandstoneFinanceTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "sandstone-finance-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "sandstone-finance-theme"
  },
  {
    "componentId": "high-contrast-accessibility-theme",
    "exportName": "HighContrastAccessibilityTheme",
    "family": "theme-wrapper",
    "category": "THEME",
    "kind": "THEME",
    "acceptedSurfaces": [
      "page.content"
    ],
    "preferredPurposes": [
      "ADMIN",
      "PRODUCT_LIKE",
      "API_TEST"
    ],
    "tags": [
      "theme",
      "high-contrast-accessibility-theme"
    ],
    "states": [
      "IDLE"
    ],
    "theme": "high-contrast-accessibility-theme"
  }
] as BlueprintPartDescriptor[];
