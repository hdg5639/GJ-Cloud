"use client";

import { createActionPart } from "../core";

export const FilterChipBar = createActionPart({
  "title": "Active filters",
  "style": "chips",
  "actions": [
    {
      "id": "open",
      "label": "Open",
      "tone": "primary"
    },
    {
      "id": "high priority",
      "label": "High priority",
      "tone": "secondary"
    },
    {
      "id": "owned by me",
      "label": "Owned by me",
      "tone": "secondary"
    }
  ]
});
