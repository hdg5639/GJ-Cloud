"use client";

import { createActionPart } from "../core";

export const DenseAdminToolbar = createActionPart({
  "title": "Admin toolbar",
  "style": "toolbar",
  "actions": [
    {
      "id": "create",
      "label": "Create",
      "tone": "primary"
    },
    {
      "id": "filter",
      "label": "Filter",
      "tone": "secondary"
    },
    {
      "id": "export",
      "label": "Export",
      "tone": "secondary"
    },
    {
      "id": "refresh",
      "label": "Refresh",
      "tone": "secondary"
    }
  ]
});
