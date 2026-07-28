"use client";

import { createWorkflowPart } from "../core";

export const ApiProductLaunchWizard = createWorkflowPart({
  "title": "API product launch",
  "description": "Publish operations, plans, documentation, and access policy.",
  "style": "wizard",
  "steps": [
    {
      "id": "product details",
      "label": "Product details",
      "description": "Complete the product details stage with validated inputs."
    },
    {
      "id": "select operations",
      "label": "Select operations",
      "description": "Complete the select operations stage with validated inputs."
    },
    {
      "id": "define plans",
      "label": "Define plans",
      "description": "Complete the define plans stage with validated inputs."
    },
    {
      "id": "review documentation",
      "label": "Review documentation",
      "description": "Complete the review documentation stage with validated inputs."
    },
    {
      "id": "publish",
      "label": "Publish",
      "description": "Complete the publish stage with validated inputs."
    }
  ],
  "completeLabel": "Launch API product"
});
