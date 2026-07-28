"use client";

import { createWorkflowPart } from "../core";

export const EventLaunchWizard = createWorkflowPart({
  "title": "Event launch",
  "description": "Prepare venue, registration, publishing, and operations.",
  "style": "wizard",
  "steps": [
    {
      "id": "event basics",
      "label": "Event basics",
      "description": "Complete the event basics stage with validated inputs."
    },
    {
      "id": "venue and capacity",
      "label": "Venue and capacity",
      "description": "Complete the venue and capacity stage with validated inputs."
    },
    {
      "id": "registration",
      "label": "Registration",
      "description": "Complete the registration stage with validated inputs."
    },
    {
      "id": "publishing",
      "label": "Publishing",
      "description": "Complete the publishing stage with validated inputs."
    },
    {
      "id": "operations check",
      "label": "Operations check",
      "description": "Complete the operations check stage with validated inputs."
    }
  ],
  "completeLabel": "Launch event"
});
