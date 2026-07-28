"use client";

import { createActionPart } from "../core";

export const ResourceQuickActions = createActionPart({
  "title": "Quick actions",
  "style": "chips",
  "actions": [
    {
      "id": "start",
      "label": "Start",
      "tone": "primary"
    },
    {
      "id": "stop",
      "label": "Stop",
      "tone": "secondary"
    },
    {
      "id": "restart",
      "label": "Restart",
      "tone": "secondary"
    },
    {
      "id": "open console",
      "label": "Open console",
      "tone": "secondary"
    }
  ]
});
