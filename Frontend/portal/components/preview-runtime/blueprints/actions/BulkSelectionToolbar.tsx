"use client";

import { createActionPart } from "../core";

export const BulkSelectionToolbar = createActionPart({
  "title": "Bulk actions",
  "style": "bulk",
  "actions": [
    {
      "id": "assign",
      "label": "Assign",
      "tone": "primary"
    },
    {
      "id": "change status",
      "label": "Change status",
      "tone": "secondary"
    },
    {
      "id": "export",
      "label": "Export",
      "tone": "secondary"
    },
    {
      "id": "delete",
      "label": "Delete",
      "tone": "danger"
    }
  ]
});
