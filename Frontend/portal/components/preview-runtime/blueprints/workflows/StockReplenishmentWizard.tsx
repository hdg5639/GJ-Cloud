"use client";

import { createWorkflowPart } from "../core";

export const StockReplenishmentWizard = createWorkflowPart({
  "title": "Stock replenishment",
  "description": "Plan replenishment using demand and current availability.",
  "style": "provision",
  "steps": [
    {
      "id": "select items",
      "label": "Select items",
      "description": "Complete the select items stage with validated inputs."
    },
    {
      "id": "review demand",
      "label": "Review demand",
      "description": "Complete the review demand stage with validated inputs."
    },
    {
      "id": "choose supplier",
      "label": "Choose supplier",
      "description": "Complete the choose supplier stage with validated inputs."
    },
    {
      "id": "set quantities",
      "label": "Set quantities",
      "description": "Complete the set quantities stage with validated inputs."
    },
    {
      "id": "submit order",
      "label": "Submit order",
      "description": "Complete the submit order stage with validated inputs."
    }
  ],
  "completeLabel": "Create replenishment"
});
