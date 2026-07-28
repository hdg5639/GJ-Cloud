"use client";

import { createWorkflowPart } from "../core";

export const TicketEscalationWizard = createWorkflowPart({
  "title": "Ticket escalation",
  "description": "Capture context and hand off a customer issue safely.",
  "style": "approval",
  "steps": [
    {
      "id": "review ticket",
      "label": "Review ticket",
      "description": "Complete the review ticket stage with validated inputs."
    },
    {
      "id": "set severity",
      "label": "Set severity",
      "description": "Complete the set severity stage with validated inputs."
    },
    {
      "id": "choose team",
      "label": "Choose team",
      "description": "Complete the choose team stage with validated inputs."
    },
    {
      "id": "prepare handoff",
      "label": "Prepare handoff",
      "description": "Complete the prepare handoff stage with validated inputs."
    },
    {
      "id": "notify customer",
      "label": "Notify customer",
      "description": "Complete the notify customer stage with validated inputs."
    }
  ],
  "completeLabel": "Escalate ticket"
});
