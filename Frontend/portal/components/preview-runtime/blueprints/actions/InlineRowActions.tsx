"use client";

import { createActionPart } from "../core";

export const InlineRowActions = createActionPart({
  "title": "Row actions",
  "style": "segmented",
  "actions": [
    {
      "id": "open",
      "label": "Open",
      "tone": "primary"
    },
    {
      "id": "edit",
      "label": "Edit",
      "tone": "secondary"
    },
    {
      "id": "duplicate",
      "label": "Duplicate",
      "tone": "secondary"
    }
  ]
});
