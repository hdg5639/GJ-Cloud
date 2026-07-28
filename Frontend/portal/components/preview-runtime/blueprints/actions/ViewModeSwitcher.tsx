"use client";

import { createActionPart } from "../core";

export const ViewModeSwitcher = createActionPart({
  "title": "View mode",
  "style": "segmented",
  "actions": [
    {
      "id": "table",
      "label": "Table",
      "tone": "primary"
    },
    {
      "id": "cards",
      "label": "Cards",
      "tone": "secondary"
    },
    {
      "id": "timeline",
      "label": "Timeline",
      "tone": "secondary"
    }
  ]
});
