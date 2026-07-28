"use client";

import { createActionPart } from "../core";

export const StatusTransitionBar = createActionPart({
  "title": "Status transition",
  "style": "toolbar",
  "actions": [
    {
      "id": "approve",
      "label": "Approve",
      "tone": "primary"
    },
    {
      "id": "pause",
      "label": "Pause",
      "tone": "secondary"
    },
    {
      "id": "reject",
      "label": "Reject",
      "tone": "danger"
    }
  ]
});
