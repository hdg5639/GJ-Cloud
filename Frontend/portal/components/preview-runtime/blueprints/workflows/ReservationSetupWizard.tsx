"use client";

import { createWorkflowPart } from "../core";

export const ReservationSetupWizard = createWorkflowPart({
  "title": "Reservation setup",
  "description": "Choose availability, guest details, policy, and payment.",
  "style": "checkout",
  "steps": [
    {
      "id": "select resource",
      "label": "Select resource",
      "description": "Complete the select resource stage with validated inputs."
    },
    {
      "id": "choose time",
      "label": "Choose time",
      "description": "Complete the choose time stage with validated inputs."
    },
    {
      "id": "add guest",
      "label": "Add guest",
      "description": "Complete the add guest stage with validated inputs."
    },
    {
      "id": "review policy",
      "label": "Review policy",
      "description": "Complete the review policy stage with validated inputs."
    },
    {
      "id": "confirm booking",
      "label": "Confirm booking",
      "description": "Complete the confirm booking stage with validated inputs."
    }
  ],
  "completeLabel": "Create reservation"
});
