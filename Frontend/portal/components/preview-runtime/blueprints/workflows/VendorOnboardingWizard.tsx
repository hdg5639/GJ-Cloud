"use client";

import { createWorkflowPart } from "../core";

export const VendorOnboardingWizard = createWorkflowPart({
  "title": "Vendor onboarding",
  "description": "Verify business, configure catalog, payouts, and launch.",
  "style": "approval",
  "steps": [
    {
      "id": "business profile",
      "label": "Business profile",
      "description": "Complete the business profile stage with validated inputs."
    },
    {
      "id": "compliance review",
      "label": "Compliance review",
      "description": "Complete the compliance review stage with validated inputs."
    },
    {
      "id": "catalog setup",
      "label": "Catalog setup",
      "description": "Complete the catalog setup stage with validated inputs."
    },
    {
      "id": "payout setup",
      "label": "Payout setup",
      "description": "Complete the payout setup stage with validated inputs."
    },
    {
      "id": "activate vendor",
      "label": "Activate vendor",
      "description": "Complete the activate vendor stage with validated inputs."
    }
  ],
  "completeLabel": "Activate vendor"
});
