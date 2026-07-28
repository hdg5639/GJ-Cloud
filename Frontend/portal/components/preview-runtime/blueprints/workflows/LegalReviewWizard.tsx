"use client";

import { createWorkflowPart } from "../core";

export const LegalReviewWizard = createWorkflowPart({
  "title": "Legal review",
  "description": "Coordinate intake, issue spotting, review, and approval.",
  "style": "approval",
  "steps": [
    {
      "id": "matter intake",
      "label": "Matter intake",
      "description": "Complete the matter intake stage with validated inputs."
    },
    {
      "id": "identify issues",
      "label": "Identify issues",
      "description": "Complete the identify issues stage with validated inputs."
    },
    {
      "id": "review documents",
      "label": "Review documents",
      "description": "Complete the review documents stage with validated inputs."
    },
    {
      "id": "resolve comments",
      "label": "Resolve comments",
      "description": "Complete the resolve comments stage with validated inputs."
    },
    {
      "id": "approve",
      "label": "Approve",
      "description": "Complete the approve stage with validated inputs."
    }
  ],
  "completeLabel": "Complete review"
});
