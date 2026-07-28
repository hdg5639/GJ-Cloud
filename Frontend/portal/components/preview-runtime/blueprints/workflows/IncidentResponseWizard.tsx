"use client";

import { createWorkflowPart } from "../core";

export const IncidentResponseWizard = createWorkflowPart({
  "title": "Incident response",
  "description": "Guide analysts from triage through containment and closure.",
  "style": "incident",
  "steps": [
    {
      "id": "triage",
      "label": "Triage",
      "description": "Complete the triage stage with validated inputs."
    },
    {
      "id": "scope impact",
      "label": "Scope impact",
      "description": "Complete the scope impact stage with validated inputs."
    },
    {
      "id": "contain threat",
      "label": "Contain threat",
      "description": "Complete the contain threat stage with validated inputs."
    },
    {
      "id": "collect evidence",
      "label": "Collect evidence",
      "description": "Complete the collect evidence stage with validated inputs."
    },
    {
      "id": "close incident",
      "label": "Close incident",
      "description": "Complete the close incident stage with validated inputs."
    }
  ],
  "completeLabel": "Complete response"
});
