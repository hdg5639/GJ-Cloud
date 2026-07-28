"use client";

import { createActionPart } from "../core";

export const SplitButtonActions = createActionPart({
  "title": "Primary action",
  "style": "segmented",
  "actions": [
    {
      "id": "run",
      "label": "Run",
      "tone": "primary"
    },
    {
      "id": "schedule",
      "label": "Schedule",
      "tone": "secondary"
    },
    {
      "id": "preview",
      "label": "Preview",
      "tone": "secondary"
    }
  ]
});
