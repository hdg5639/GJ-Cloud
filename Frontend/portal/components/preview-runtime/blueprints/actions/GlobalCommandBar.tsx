"use client";

import { createActionPart } from "../core";

export const GlobalCommandBar = createActionPart({
  "title": "Global commands",
  "style": "commandbar",
  "actions": [
    {
      "id": "search",
      "label": "Search",
      "tone": "primary"
    },
    {
      "id": "create",
      "label": "Create",
      "tone": "secondary"
    },
    {
      "id": "deploy",
      "label": "Deploy",
      "tone": "secondary"
    },
    {
      "id": "open logs",
      "label": "Open logs",
      "tone": "secondary"
    }
  ]
});
