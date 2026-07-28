"use client";

import { createWorkflowPart } from "../core";

export const ReleasePromotionWizard = createWorkflowPart({
  "title": "Release promotion",
  "description": "Validate and promote a release through environments.",
  "style": "migration",
  "steps": [
    {
      "id": "select release",
      "label": "Select release",
      "description": "Complete the select release stage with validated inputs."
    },
    {
      "id": "review changes",
      "label": "Review changes",
      "description": "Complete the review changes stage with validated inputs."
    },
    {
      "id": "run checks",
      "label": "Run checks",
      "description": "Complete the run checks stage with validated inputs."
    },
    {
      "id": "choose rollout",
      "label": "Choose rollout",
      "description": "Complete the choose rollout stage with validated inputs."
    },
    {
      "id": "promote",
      "label": "Promote",
      "description": "Complete the promote stage with validated inputs."
    }
  ],
  "completeLabel": "Promote release"
});
