export interface MegaFlowPreset {
  id: string;
  name: string;
  category: string;
  trigger: "USER_ACTION" | "PAGE_LOAD";
  steps: Array<{ id: string; type: "API_CALL" | "SET_CONTEXT" | "NAVIGATE" | "POLL" | "WAIT" | "CONDITION" | "SHOW_SUCCESS" | "SHOW_ERROR" | "REFRESH_BINDING"; label: string }>;
  tags: string[];
}

export const MEGA_FLOW_PRESETS: MegaFlowPreset[] = [
  {
    "id": "incident-response-flow",
    "name": "Incident response",
    "category": "SECURITY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "triage",
        "type": "SET_CONTEXT",
        "label": "Triage"
      },
      {
        "id": "scope impact",
        "type": "SET_CONTEXT",
        "label": "Scope impact"
      },
      {
        "id": "contain threat",
        "type": "SET_CONTEXT",
        "label": "Contain threat"
      },
      {
        "id": "collect evidence",
        "type": "SET_CONTEXT",
        "label": "Collect evidence"
      },
      {
        "id": "close incident",
        "type": "API_CALL",
        "label": "Close incident"
      }
    ],
    "tags": [
      "security",
      "incident",
      "response"
    ]
  },
  {
    "id": "vulnerability-remediation-flow",
    "name": "Vulnerability remediation",
    "category": "SECURITY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review finding",
        "type": "SET_CONTEXT",
        "label": "Review finding"
      },
      {
        "id": "assign owner",
        "type": "SET_CONTEXT",
        "label": "Assign owner"
      },
      {
        "id": "plan remediation",
        "type": "SET_CONTEXT",
        "label": "Plan remediation"
      },
      {
        "id": "verify fix",
        "type": "SET_CONTEXT",
        "label": "Verify fix"
      },
      {
        "id": "close finding",
        "type": "API_CALL",
        "label": "Close finding"
      }
    ],
    "tags": [
      "security",
      "vulnerability",
      "remediation"
    ]
  },
  {
    "id": "ticket-escalation-flow",
    "name": "Ticket escalation",
    "category": "SUPPORT",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review ticket",
        "type": "SET_CONTEXT",
        "label": "Review ticket"
      },
      {
        "id": "set severity",
        "type": "SET_CONTEXT",
        "label": "Set severity"
      },
      {
        "id": "choose team",
        "type": "SET_CONTEXT",
        "label": "Choose team"
      },
      {
        "id": "prepare handoff",
        "type": "SET_CONTEXT",
        "label": "Prepare handoff"
      },
      {
        "id": "notify customer",
        "type": "API_CALL",
        "label": "Notify customer"
      }
    ],
    "tags": [
      "support",
      "ticket",
      "escalation"
    ]
  },
  {
    "id": "customer-renewal-flow",
    "name": "Customer renewal",
    "category": "CRM",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review account",
        "type": "SET_CONTEXT",
        "label": "Review account"
      },
      {
        "id": "assess health",
        "type": "SET_CONTEXT",
        "label": "Assess health"
      },
      {
        "id": "prepare offer",
        "type": "SET_CONTEXT",
        "label": "Prepare offer"
      },
      {
        "id": "approve terms",
        "type": "SET_CONTEXT",
        "label": "Approve terms"
      },
      {
        "id": "send renewal",
        "type": "API_CALL",
        "label": "Send renewal"
      }
    ],
    "tags": [
      "crm",
      "renewal",
      "customer"
    ]
  },
  {
    "id": "payment-reconciliation-flow",
    "name": "Payment reconciliation",
    "category": "FINANCE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "load records",
        "type": "SET_CONTEXT",
        "label": "Load records"
      },
      {
        "id": "match entries",
        "type": "SET_CONTEXT",
        "label": "Match entries"
      },
      {
        "id": "review exceptions",
        "type": "SET_CONTEXT",
        "label": "Review exceptions"
      },
      {
        "id": "post adjustments",
        "type": "SET_CONTEXT",
        "label": "Post adjustments"
      },
      {
        "id": "close batch",
        "type": "API_CALL",
        "label": "Close batch"
      }
    ],
    "tags": [
      "finance",
      "payment",
      "reconciliation"
    ]
  },
  {
    "id": "subscription-upgrade-flow",
    "name": "Subscription upgrade",
    "category": "BILLING",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "choose plan",
        "type": "SET_CONTEXT",
        "label": "Choose plan"
      },
      {
        "id": "configure seats",
        "type": "SET_CONTEXT",
        "label": "Configure seats"
      },
      {
        "id": "preview charges",
        "type": "SET_CONTEXT",
        "label": "Preview charges"
      },
      {
        "id": "confirm timing",
        "type": "SET_CONTEXT",
        "label": "Confirm timing"
      },
      {
        "id": "apply upgrade",
        "type": "API_CALL",
        "label": "Apply upgrade"
      }
    ],
    "tags": [
      "billing",
      "subscription",
      "upgrade"
    ]
  },
  {
    "id": "stock-replenishment-flow",
    "name": "Stock replenishment",
    "category": "INVENTORY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "select items",
        "type": "SET_CONTEXT",
        "label": "Select items"
      },
      {
        "id": "review demand",
        "type": "SET_CONTEXT",
        "label": "Review demand"
      },
      {
        "id": "choose supplier",
        "type": "SET_CONTEXT",
        "label": "Choose supplier"
      },
      {
        "id": "set quantities",
        "type": "SET_CONTEXT",
        "label": "Set quantities"
      },
      {
        "id": "submit order",
        "type": "API_CALL",
        "label": "Submit order"
      }
    ],
    "tags": [
      "inventory",
      "stock",
      "replenishment"
    ]
  },
  {
    "id": "shipment-exception-flow",
    "name": "Shipment exception",
    "category": "LOGISTICS",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "identify issue",
        "type": "SET_CONTEXT",
        "label": "Identify issue"
      },
      {
        "id": "assess impact",
        "type": "SET_CONTEXT",
        "label": "Assess impact"
      },
      {
        "id": "choose resolution",
        "type": "SET_CONTEXT",
        "label": "Choose resolution"
      },
      {
        "id": "reschedule route",
        "type": "SET_CONTEXT",
        "label": "Reschedule route"
      },
      {
        "id": "notify parties",
        "type": "API_CALL",
        "label": "Notify parties"
      }
    ],
    "tags": [
      "logistics",
      "shipment",
      "exception"
    ]
  },
  {
    "id": "reservation-setup-flow",
    "name": "Reservation setup",
    "category": "BOOKING",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "select resource",
        "type": "SET_CONTEXT",
        "label": "Select resource"
      },
      {
        "id": "choose time",
        "type": "SET_CONTEXT",
        "label": "Choose time"
      },
      {
        "id": "add guest",
        "type": "SET_CONTEXT",
        "label": "Add guest"
      },
      {
        "id": "review policy",
        "type": "SET_CONTEXT",
        "label": "Review policy"
      },
      {
        "id": "confirm booking",
        "type": "API_CALL",
        "label": "Confirm booking"
      }
    ],
    "tags": [
      "booking",
      "reservation",
      "setup"
    ]
  },
  {
    "id": "event-launch-flow",
    "name": "Event launch",
    "category": "EVENTS",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "event basics",
        "type": "SET_CONTEXT",
        "label": "Event basics"
      },
      {
        "id": "venue and capacity",
        "type": "SET_CONTEXT",
        "label": "Venue and capacity"
      },
      {
        "id": "registration",
        "type": "SET_CONTEXT",
        "label": "Registration"
      },
      {
        "id": "publishing",
        "type": "SET_CONTEXT",
        "label": "Publishing"
      },
      {
        "id": "operations check",
        "type": "API_CALL",
        "label": "Operations check"
      }
    ],
    "tags": [
      "events",
      "launch",
      "venue"
    ]
  },
  {
    "id": "course-publishing-flow",
    "name": "Course publishing",
    "category": "EDUCATION",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "course details",
        "type": "SET_CONTEXT",
        "label": "Course details"
      },
      {
        "id": "curriculum",
        "type": "SET_CONTEXT",
        "label": "Curriculum"
      },
      {
        "id": "assessments",
        "type": "SET_CONTEXT",
        "label": "Assessments"
      },
      {
        "id": "access policy",
        "type": "SET_CONTEXT",
        "label": "Access policy"
      },
      {
        "id": "publish",
        "type": "API_CALL",
        "label": "Publish"
      }
    ],
    "tags": [
      "education",
      "course",
      "publishing"
    ]
  },
  {
    "id": "employee-onboarding-flow",
    "name": "Employee onboarding",
    "category": "HR",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "employee profile",
        "type": "SET_CONTEXT",
        "label": "Employee profile"
      },
      {
        "id": "role and team",
        "type": "SET_CONTEXT",
        "label": "Role and team"
      },
      {
        "id": "system access",
        "type": "SET_CONTEXT",
        "label": "System access"
      },
      {
        "id": "equipment",
        "type": "SET_CONTEXT",
        "label": "Equipment"
      },
      {
        "id": "welcome plan",
        "type": "API_CALL",
        "label": "Welcome plan"
      }
    ],
    "tags": [
      "hr",
      "employee",
      "onboarding"
    ]
  },
  {
    "id": "candidate-hiring-flow",
    "name": "Candidate hiring",
    "category": "HR",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review interviews",
        "type": "SET_CONTEXT",
        "label": "Review interviews"
      },
      {
        "id": "confirm decision",
        "type": "SET_CONTEXT",
        "label": "Confirm decision"
      },
      {
        "id": "prepare offer",
        "type": "SET_CONTEXT",
        "label": "Prepare offer"
      },
      {
        "id": "approve compensation",
        "type": "SET_CONTEXT",
        "label": "Approve compensation"
      },
      {
        "id": "send offer",
        "type": "API_CALL",
        "label": "Send offer"
      }
    ],
    "tags": [
      "hr",
      "candidate",
      "hiring"
    ]
  },
  {
    "id": "api-product-launch-flow",
    "name": "API product launch",
    "category": "DEVELOPER",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "product details",
        "type": "SET_CONTEXT",
        "label": "Product details"
      },
      {
        "id": "select operations",
        "type": "SET_CONTEXT",
        "label": "Select operations"
      },
      {
        "id": "define plans",
        "type": "SET_CONTEXT",
        "label": "Define plans"
      },
      {
        "id": "review documentation",
        "type": "SET_CONTEXT",
        "label": "Review documentation"
      },
      {
        "id": "publish",
        "type": "API_CALL",
        "label": "Publish"
      }
    ],
    "tags": [
      "developer",
      "api",
      "launch"
    ]
  },
  {
    "id": "release-promotion-flow",
    "name": "Release promotion",
    "category": "DEVELOPER",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "select release",
        "type": "SET_CONTEXT",
        "label": "Select release"
      },
      {
        "id": "review changes",
        "type": "SET_CONTEXT",
        "label": "Review changes"
      },
      {
        "id": "run checks",
        "type": "SET_CONTEXT",
        "label": "Run checks"
      },
      {
        "id": "choose rollout",
        "type": "SET_CONTEXT",
        "label": "Choose rollout"
      },
      {
        "id": "promote",
        "type": "API_CALL",
        "label": "Promote"
      }
    ],
    "tags": [
      "developer",
      "release",
      "deployment"
    ]
  },
  {
    "id": "model-deployment-flow",
    "name": "Model deployment",
    "category": "AI",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "select model",
        "type": "SET_CONTEXT",
        "label": "Select model"
      },
      {
        "id": "review evaluation",
        "type": "SET_CONTEXT",
        "label": "Review evaluation"
      },
      {
        "id": "configure serving",
        "type": "SET_CONTEXT",
        "label": "Configure serving"
      },
      {
        "id": "approve risk",
        "type": "SET_CONTEXT",
        "label": "Approve risk"
      },
      {
        "id": "deploy",
        "type": "API_CALL",
        "label": "Deploy"
      }
    ],
    "tags": [
      "ai",
      "model",
      "deployment"
    ]
  },
  {
    "id": "device-provisioning-flow",
    "name": "Device provisioning",
    "category": "IOT",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "register device",
        "type": "SET_CONTEXT",
        "label": "Register device"
      },
      {
        "id": "assign site",
        "type": "SET_CONTEXT",
        "label": "Assign site"
      },
      {
        "id": "apply policy",
        "type": "SET_CONTEXT",
        "label": "Apply policy"
      },
      {
        "id": "install credentials",
        "type": "SET_CONTEXT",
        "label": "Install credentials"
      },
      {
        "id": "verify connection",
        "type": "API_CALL",
        "label": "Verify connection"
      }
    ],
    "tags": [
      "iot",
      "device",
      "provisioning"
    ]
  },
  {
    "id": "property-listing-flow",
    "name": "Property listing",
    "category": "REAL_ESTATE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "property details",
        "type": "SET_CONTEXT",
        "label": "Property details"
      },
      {
        "id": "media",
        "type": "SET_CONTEXT",
        "label": "Media"
      },
      {
        "id": "pricing",
        "type": "SET_CONTEXT",
        "label": "Pricing"
      },
      {
        "id": "availability",
        "type": "SET_CONTEXT",
        "label": "Availability"
      },
      {
        "id": "publish",
        "type": "API_CALL",
        "label": "Publish"
      }
    ],
    "tags": [
      "real-estate",
      "property",
      "listing"
    ]
  },
  {
    "id": "vendor-onboarding-flow",
    "name": "Vendor onboarding",
    "category": "MARKETPLACE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "business profile",
        "type": "SET_CONTEXT",
        "label": "Business profile"
      },
      {
        "id": "compliance review",
        "type": "SET_CONTEXT",
        "label": "Compliance review"
      },
      {
        "id": "catalog setup",
        "type": "SET_CONTEXT",
        "label": "Catalog setup"
      },
      {
        "id": "payout setup",
        "type": "SET_CONTEXT",
        "label": "Payout setup"
      },
      {
        "id": "activate vendor",
        "type": "API_CALL",
        "label": "Activate vendor"
      }
    ],
    "tags": [
      "marketplace",
      "vendor",
      "onboarding"
    ]
  },
  {
    "id": "legal-review-flow",
    "name": "Legal review",
    "category": "LEGAL",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "matter intake",
        "type": "SET_CONTEXT",
        "label": "Matter intake"
      },
      {
        "id": "identify issues",
        "type": "SET_CONTEXT",
        "label": "Identify issues"
      },
      {
        "id": "review documents",
        "type": "SET_CONTEXT",
        "label": "Review documents"
      },
      {
        "id": "resolve comments",
        "type": "SET_CONTEXT",
        "label": "Resolve comments"
      },
      {
        "id": "approve",
        "type": "API_CALL",
        "label": "Approve"
      }
    ],
    "tags": [
      "legal",
      "review",
      "approval"
    ]
  },
  {
    "id": "media-publishing-flow",
    "name": "Media publishing",
    "category": "MEDIA",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "select asset",
        "type": "SET_CONTEXT",
        "label": "Select asset"
      },
      {
        "id": "review quality",
        "type": "SET_CONTEXT",
        "label": "Review quality"
      },
      {
        "id": "verify rights",
        "type": "SET_CONTEXT",
        "label": "Verify rights"
      },
      {
        "id": "choose channels",
        "type": "SET_CONTEXT",
        "label": "Choose channels"
      },
      {
        "id": "publish",
        "type": "API_CALL",
        "label": "Publish"
      }
    ],
    "tags": [
      "media",
      "asset",
      "publishing"
    ]
  },
  {
    "id": "knowledge-approval-flow",
    "name": "Knowledge approval",
    "category": "KNOWLEDGE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review content",
        "type": "SET_CONTEXT",
        "label": "Review content"
      },
      {
        "id": "verify references",
        "type": "SET_CONTEXT",
        "label": "Verify references"
      },
      {
        "id": "assign owner",
        "type": "SET_CONTEXT",
        "label": "Assign owner"
      },
      {
        "id": "resolve feedback",
        "type": "SET_CONTEXT",
        "label": "Resolve feedback"
      },
      {
        "id": "publish",
        "type": "API_CALL",
        "label": "Publish"
      }
    ],
    "tags": [
      "knowledge",
      "article",
      "approval"
    ]
  },
  {
    "id": "acknowledge-alert-flow",
    "name": "Acknowledge alert",
    "category": "SECURITY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Acknowledge"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "security",
      "review"
    ]
  },
  {
    "id": "escalate-incident-flow",
    "name": "Escalate incident",
    "category": "SECURITY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Escalate"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "security",
      "danger"
    ]
  },
  {
    "id": "merge-tickets-flow",
    "name": "Merge tickets",
    "category": "SUPPORT",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Merge tickets"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "support",
      "impact"
    ]
  },
  {
    "id": "send-reply-flow",
    "name": "Send customer reply",
    "category": "SUPPORT",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Send reply"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "support",
      "form"
    ]
  },
  {
    "id": "issue-refund-flow",
    "name": "Issue refund",
    "category": "FINANCE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Issue refund"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "finance",
      "danger"
    ]
  },
  {
    "id": "capture-payment-flow",
    "name": "Capture payment",
    "category": "FINANCE",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Capture"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "finance",
      "confirm"
    ]
  },
  {
    "id": "adjust-inventory-flow",
    "name": "Adjust inventory",
    "category": "INVENTORY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Apply adjustment"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "inventory",
      "form"
    ]
  },
  {
    "id": "transfer-stock-flow",
    "name": "Transfer stock",
    "category": "INVENTORY",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Create transfer"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "inventory",
      "form"
    ]
  },
  {
    "id": "reassign-shipment-flow",
    "name": "Reassign shipment",
    "category": "LOGISTICS",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Reassign"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "logistics",
      "picker"
    ]
  },
  {
    "id": "delivery-exception-flow",
    "name": "Resolve delivery exception",
    "category": "LOGISTICS",
    "trigger": "USER_ACTION",
    "steps": [
      {
        "id": "review",
        "type": "SET_CONTEXT",
        "label": "Review input"
      },
      {
        "id": "submit",
        "type": "API_CALL",
        "label": "Apply resolution"
      },
      {
        "id": "refresh",
        "type": "REFRESH_BINDING",
        "label": "Refresh related data"
      }
    ],
    "tags": [
      "logistics",
      "impact"
    ]
  }
] as MegaFlowPreset[];
