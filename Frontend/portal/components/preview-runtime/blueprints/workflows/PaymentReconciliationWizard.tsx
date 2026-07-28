"use client";

import { createWorkflowPart } from "../core";

export const PaymentReconciliationWizard = createWorkflowPart({
  "title": "Payment reconciliation",
  "description": "Match payments, resolve differences, and close the period.",
  "style": "migration",
  "steps": [
    {
      "id": "load records",
      "label": "Load records",
      "description": "Complete the load records stage with validated inputs."
    },
    {
      "id": "match entries",
      "label": "Match entries",
      "description": "Complete the match entries stage with validated inputs."
    },
    {
      "id": "review exceptions",
      "label": "Review exceptions",
      "description": "Complete the review exceptions stage with validated inputs."
    },
    {
      "id": "post adjustments",
      "label": "Post adjustments",
      "description": "Complete the post adjustments stage with validated inputs."
    },
    {
      "id": "close batch",
      "label": "Close batch",
      "description": "Complete the close batch stage with validated inputs."
    }
  ],
  "completeLabel": "Complete reconciliation"
});
