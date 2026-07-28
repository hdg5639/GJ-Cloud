"use client";

import { createWorkflowPart } from "../core";

export const CustomerRenewalWizard = createWorkflowPart({
  "title": "Customer renewal",
  "description": "Review health, commercial terms, and renewal actions.",
  "style": "checkout",
  "steps": [
    {
      "id": "review account",
      "label": "Review account",
      "description": "Complete the review account stage with validated inputs."
    },
    {
      "id": "assess health",
      "label": "Assess health",
      "description": "Complete the assess health stage with validated inputs."
    },
    {
      "id": "prepare offer",
      "label": "Prepare offer",
      "description": "Complete the prepare offer stage with validated inputs."
    },
    {
      "id": "approve terms",
      "label": "Approve terms",
      "description": "Complete the approve terms stage with validated inputs."
    },
    {
      "id": "send renewal",
      "label": "Send renewal",
      "description": "Complete the send renewal stage with validated inputs."
    }
  ],
  "completeLabel": "Start renewal"
});
