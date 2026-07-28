export interface ProductBlueprintRecipe {
  id: string;
  name: string;
  category: string;
  layoutId: string;
  dashboardId: string;
  collectionId: string;
  detailId: string;
  workflowId: string;
  themeId: string;
  suggestedSlots: Record<string, string[]>;
}

export const PRODUCT_BLUEPRINT_RECIPES: ProductBlueprintRecipe[] = [
  {
    "id": "security-operations-center",
    "name": "Security operations center",
    "category": "SECURITY",
    "layoutId": "security-command-layout",
    "dashboardId": "security-threat-dashboard",
    "collectionId": "threat-event-stream",
    "detailId": "threat-incident-detail",
    "workflowId": "incident-response-wizard",
    "themeId": "crimson-security-theme",
    "suggestedSlots": {
      "page.summary": [
        "security-threat-dashboard"
      ],
      "page.main": [
        "threat-event-stream"
      ],
      "page.aside": [
        "threat-incident-detail"
      ],
      "page.overlay": [
        "incident-response-wizard"
      ]
    }
  },
  {
    "id": "customer-support-desk",
    "name": "Customer support desk",
    "category": "SUPPORT",
    "layoutId": "support-console-layout",
    "dashboardId": "support-sla-dashboard",
    "collectionId": "support-ticket-inbox",
    "detailId": "support-ticket-detail",
    "workflowId": "ticket-escalation-wizard",
    "themeId": "enterprise-blue-theme",
    "suggestedSlots": {
      "page.summary": [
        "support-sla-dashboard"
      ],
      "page.main": [
        "support-ticket-inbox"
      ],
      "page.aside": [
        "support-ticket-detail"
      ],
      "page.overlay": [
        "ticket-escalation-wizard"
      ]
    }
  },
  {
    "id": "finance-operations",
    "name": "Finance operations",
    "category": "FINANCE",
    "layoutId": "finance-ledger-layout",
    "dashboardId": "finance-cashflow-dashboard",
    "collectionId": "transaction-ledger",
    "detailId": "transaction-detail",
    "workflowId": "payment-reconciliation-wizard",
    "themeId": "sandstone-finance-theme",
    "suggestedSlots": {
      "page.summary": [
        "finance-cashflow-dashboard"
      ],
      "page.main": [
        "transaction-ledger"
      ],
      "page.aside": [
        "transaction-detail"
      ],
      "page.overlay": [
        "payment-reconciliation-wizard"
      ]
    }
  },
  {
    "id": "subscription-billing",
    "name": "Subscription billing",
    "category": "BILLING",
    "layoutId": "billing-workspace-layout",
    "dashboardId": "billing-revenue-dashboard",
    "collectionId": "invoice-collection",
    "detailId": "invoice-detail",
    "workflowId": "subscription-upgrade-wizard",
    "themeId": "enterprise-blue-theme",
    "suggestedSlots": {
      "page.summary": [
        "billing-revenue-dashboard"
      ],
      "page.main": [
        "invoice-collection"
      ],
      "page.aside": [
        "invoice-detail"
      ],
      "page.overlay": [
        "subscription-upgrade-wizard"
      ]
    }
  },
  {
    "id": "warehouse-control",
    "name": "Warehouse control",
    "category": "INVENTORY",
    "layoutId": "inventory-warehouse-layout",
    "dashboardId": "warehouse-capacity-dashboard",
    "collectionId": "inventory-sku-matrix",
    "detailId": "inventory-item-detail",
    "workflowId": "stock-replenishment-wizard",
    "themeId": "terminal-green-theme",
    "suggestedSlots": {
      "page.summary": [
        "warehouse-capacity-dashboard"
      ],
      "page.main": [
        "inventory-sku-matrix"
      ],
      "page.aside": [
        "inventory-item-detail"
      ],
      "page.overlay": [
        "stock-replenishment-wizard"
      ]
    }
  },
  {
    "id": "logistics-dispatch",
    "name": "Logistics dispatch",
    "category": "LOGISTICS",
    "layoutId": "logistics-dispatch-layout",
    "dashboardId": "logistics-fleet-dashboard",
    "collectionId": "shipment-tracking-board",
    "detailId": "shipment-detail",
    "workflowId": "shipment-exception-wizard",
    "themeId": "sunset-operations-theme",
    "suggestedSlots": {
      "page.summary": [
        "logistics-fleet-dashboard"
      ],
      "page.main": [
        "shipment-tracking-board"
      ],
      "page.aside": [
        "shipment-detail"
      ],
      "page.overlay": [
        "shipment-exception-wizard"
      ]
    }
  },
  {
    "id": "booking-platform",
    "name": "Booking platform",
    "category": "BOOKING",
    "layoutId": "booking-planner-layout",
    "dashboardId": "booking-occupancy-dashboard",
    "collectionId": "reservation-calendar",
    "detailId": "reservation-detail",
    "workflowId": "reservation-setup-wizard",
    "themeId": "soft-pastel-theme",
    "suggestedSlots": {
      "page.summary": [
        "booking-occupancy-dashboard"
      ],
      "page.main": [
        "reservation-calendar"
      ],
      "page.aside": [
        "reservation-detail"
      ],
      "page.overlay": [
        "reservation-setup-wizard"
      ]
    }
  },
  {
    "id": "event-platform",
    "name": "Event platform",
    "category": "EVENTS",
    "layoutId": "event-operations-layout",
    "dashboardId": "event-attendance-dashboard",
    "collectionId": "venue-seat-map",
    "detailId": "event-detail",
    "workflowId": "event-launch-wizard",
    "themeId": "neon-cyber-theme",
    "suggestedSlots": {
      "page.summary": [
        "event-attendance-dashboard"
      ],
      "page.main": [
        "venue-seat-map"
      ],
      "page.aside": [
        "event-detail"
      ],
      "page.overlay": [
        "event-launch-wizard"
      ]
    }
  },
  {
    "id": "learning-management",
    "name": "Learning management",
    "category": "EDUCATION",
    "layoutId": "learning-portal-layout",
    "dashboardId": "learning-progress-dashboard",
    "collectionId": "course-catalog-grid",
    "detailId": "course-detail",
    "workflowId": "course-publishing-wizard",
    "themeId": "soft-pastel-theme",
    "suggestedSlots": {
      "page.summary": [
        "learning-progress-dashboard"
      ],
      "page.main": [
        "course-catalog-grid"
      ],
      "page.aside": [
        "course-detail"
      ],
      "page.overlay": [
        "course-publishing-wizard"
      ]
    }
  },
  {
    "id": "people-operations",
    "name": "People operations",
    "category": "HR",
    "layoutId": "hr-people-ops-layout",
    "dashboardId": "hr-workforce-dashboard",
    "collectionId": "employee-directory-grid",
    "detailId": "employee-profile-detail",
    "workflowId": "employee-onboarding-wizard",
    "themeId": "enterprise-blue-theme",
    "suggestedSlots": {
      "page.summary": [
        "hr-workforce-dashboard"
      ],
      "page.main": [
        "employee-directory-grid"
      ],
      "page.aside": [
        "employee-profile-detail"
      ],
      "page.overlay": [
        "employee-onboarding-wizard"
      ]
    }
  },
  {
    "id": "developer-platform",
    "name": "Developer platform",
    "category": "DEVELOPER",
    "layoutId": "developer-platform-layout",
    "dashboardId": "api-reliability-dashboard",
    "collectionId": "api-endpoint-catalog",
    "detailId": "api-product-detail",
    "workflowId": "api-product-launch-wizard",
    "themeId": "terminal-green-theme",
    "suggestedSlots": {
      "page.summary": [
        "api-reliability-dashboard"
      ],
      "page.main": [
        "api-endpoint-catalog"
      ],
      "page.aside": [
        "api-product-detail"
      ],
      "page.overlay": [
        "api-product-launch-wizard"
      ]
    }
  },
  {
    "id": "ai-model-studio",
    "name": "AI model studio",
    "category": "AI",
    "layoutId": "ai-studio-layout",
    "dashboardId": "ai-model-ops-dashboard",
    "collectionId": "model-registry-collection",
    "detailId": "model-detail",
    "workflowId": "model-deployment-wizard",
    "themeId": "neon-cyber-theme",
    "suggestedSlots": {
      "page.summary": [
        "ai-model-ops-dashboard"
      ],
      "page.main": [
        "model-registry-collection"
      ],
      "page.aside": [
        "model-detail"
      ],
      "page.overlay": [
        "model-deployment-wizard"
      ]
    }
  },
  {
    "id": "iot-device-control",
    "name": "IoT device control",
    "category": "IOT",
    "layoutId": "iot-control-layout",
    "dashboardId": "iot-device-fleet-dashboard",
    "collectionId": "device-topology-list",
    "detailId": "device-detail",
    "workflowId": "device-provisioning-wizard",
    "themeId": "ocean-data-theme",
    "suggestedSlots": {
      "page.summary": [
        "iot-device-fleet-dashboard"
      ],
      "page.main": [
        "device-topology-list"
      ],
      "page.aside": [
        "device-detail"
      ],
      "page.overlay": [
        "device-provisioning-wizard"
      ]
    }
  },
  {
    "id": "property-management",
    "name": "Property management",
    "category": "REAL_ESTATE",
    "layoutId": "real-estate-portfolio-layout",
    "dashboardId": "real-estate-portfolio-dashboard",
    "collectionId": "property-listing-grid",
    "detailId": "property-detail",
    "workflowId": "property-listing-wizard",
    "themeId": "editorial-paper-theme",
    "suggestedSlots": {
      "page.summary": [
        "real-estate-portfolio-dashboard"
      ],
      "page.main": [
        "property-listing-grid"
      ],
      "page.aside": [
        "property-detail"
      ],
      "page.overlay": [
        "property-listing-wizard"
      ]
    }
  },
  {
    "id": "community-platform",
    "name": "Community platform",
    "category": "COMMUNITY",
    "layoutId": "social-community-layout",
    "dashboardId": "community-engagement-dashboard",
    "collectionId": "community-feed",
    "detailId": "community-member-detail",
    "workflowId": "knowledge-approval-wizard",
    "themeId": "lavender-creator-theme",
    "suggestedSlots": {
      "page.summary": [
        "community-engagement-dashboard"
      ],
      "page.main": [
        "community-feed"
      ],
      "page.aside": [
        "community-member-detail"
      ],
      "page.overlay": [
        "knowledge-approval-wizard"
      ]
    }
  },
  {
    "id": "marketplace-admin",
    "name": "Marketplace administration",
    "category": "MARKETPLACE",
    "layoutId": "marketplace-operations-layout",
    "dashboardId": "marketplace-liquidity-dashboard",
    "collectionId": "vendor-marketplace-grid",
    "detailId": "vendor-detail",
    "workflowId": "vendor-onboarding-wizard",
    "themeId": "warm-commerce-theme",
    "suggestedSlots": {
      "page.summary": [
        "marketplace-liquidity-dashboard"
      ],
      "page.main": [
        "vendor-marketplace-grid"
      ],
      "page.aside": [
        "vendor-detail"
      ],
      "page.overlay": [
        "vendor-onboarding-wizard"
      ]
    }
  },
  {
    "id": "travel-operations",
    "name": "Travel operations",
    "category": "TRAVEL",
    "layoutId": "travel-planner-layout",
    "dashboardId": "travel-operations-dashboard",
    "collectionId": "trip-itinerary-collection",
    "detailId": "trip-detail",
    "workflowId": "reservation-setup-wizard",
    "themeId": "glass-aurora-theme",
    "suggestedSlots": {
      "page.summary": [
        "travel-operations-dashboard"
      ],
      "page.main": [
        "trip-itinerary-collection"
      ],
      "page.aside": [
        "trip-detail"
      ],
      "page.overlay": [
        "reservation-setup-wizard"
      ]
    }
  },
  {
    "id": "legal-matter-management",
    "name": "Legal matter management",
    "category": "LEGAL",
    "layoutId": "legal-case-layout",
    "dashboardId": "legal-matter-dashboard",
    "collectionId": "legal-case-docket",
    "detailId": "legal-matter-detail",
    "workflowId": "legal-review-wizard",
    "themeId": "editorial-paper-theme",
    "suggestedSlots": {
      "page.summary": [
        "legal-matter-dashboard"
      ],
      "page.main": [
        "legal-case-docket"
      ],
      "page.aside": [
        "legal-matter-detail"
      ],
      "page.overlay": [
        "legal-review-wizard"
      ]
    }
  },
  {
    "id": "media-production",
    "name": "Media production",
    "category": "MEDIA",
    "layoutId": "media-production-layout",
    "dashboardId": "media-pipeline-dashboard",
    "collectionId": "asset-production-board",
    "detailId": "media-asset-detail",
    "workflowId": "media-publishing-wizard",
    "themeId": "lavender-creator-theme",
    "suggestedSlots": {
      "page.summary": [
        "media-pipeline-dashboard"
      ],
      "page.main": [
        "asset-production-board"
      ],
      "page.aside": [
        "media-asset-detail"
      ],
      "page.overlay": [
        "media-publishing-wizard"
      ]
    }
  },
  {
    "id": "knowledge-platform",
    "name": "Knowledge platform",
    "category": "KNOWLEDGE",
    "layoutId": "knowledge-base-layout",
    "dashboardId": "knowledge-health-dashboard",
    "collectionId": "course-catalog-grid",
    "detailId": "knowledge-article-detail",
    "workflowId": "knowledge-approval-wizard",
    "themeId": "editorial-paper-theme",
    "suggestedSlots": {
      "page.summary": [
        "knowledge-health-dashboard"
      ],
      "page.main": [
        "course-catalog-grid"
      ],
      "page.aside": [
        "knowledge-article-detail"
      ],
      "page.overlay": [
        "knowledge-approval-wizard"
      ]
    }
  }
] as ProductBlueprintRecipe[];
