"use client";

import { createActionPart } from "../core";

export const SavedViewBar = createActionPart({
  "title": "Saved views",
  "style": "chips",
  "actions": [
    {
      "id": "my queue",
      "label": "My queue",
      "tone": "primary"
    },
    {
      "id": "at risk",
      "label": "At risk",
      "tone": "secondary"
    },
    {
      "id": "recently updated",
      "label": "Recently updated",
      "tone": "secondary"
    }
  ]
});
