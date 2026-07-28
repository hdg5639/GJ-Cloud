"use client";

import { createWorkflowPart } from "../core";

export const CandidateHiringWizard = createWorkflowPart({
  "title": "Candidate hiring",
  "description": "Review decision, prepare offer, and start onboarding.",
  "style": "approval",
  "steps": [
    {
      "id": "review interviews",
      "label": "Review interviews",
      "description": "Complete the review interviews stage with validated inputs."
    },
    {
      "id": "confirm decision",
      "label": "Confirm decision",
      "description": "Complete the confirm decision stage with validated inputs."
    },
    {
      "id": "prepare offer",
      "label": "Prepare offer",
      "description": "Complete the prepare offer stage with validated inputs."
    },
    {
      "id": "approve compensation",
      "label": "Approve compensation",
      "description": "Complete the approve compensation stage with validated inputs."
    },
    {
      "id": "send offer",
      "label": "Send offer",
      "description": "Complete the send offer stage with validated inputs."
    }
  ],
  "completeLabel": "Hire candidate"
});
