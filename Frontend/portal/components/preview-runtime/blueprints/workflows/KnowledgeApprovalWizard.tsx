"use client";

import { createWorkflowPart } from "../core";

export const KnowledgeApprovalWizard = createWorkflowPart({
  "title": "Knowledge approval",
  "description": "Verify accuracy, ownership, references, and publish readiness.",
  "style": "approval",
  "steps": [
    {
      "id": "review content",
      "label": "Review content",
      "description": "Complete the review content stage with validated inputs."
    },
    {
      "id": "verify references",
      "label": "Verify references",
      "description": "Complete the verify references stage with validated inputs."
    },
    {
      "id": "assign owner",
      "label": "Assign owner",
      "description": "Complete the assign owner stage with validated inputs."
    },
    {
      "id": "resolve feedback",
      "label": "Resolve feedback",
      "description": "Complete the resolve feedback stage with validated inputs."
    },
    {
      "id": "publish",
      "label": "Publish",
      "description": "Complete the publish stage with validated inputs."
    }
  ],
  "completeLabel": "Publish article"
});
