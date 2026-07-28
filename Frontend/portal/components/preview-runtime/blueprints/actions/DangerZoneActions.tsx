"use client";

import { createActionPart } from "../core";

export const DangerZoneActions = createActionPart({
  "title": "Danger zone",
  "style": "danger",
  "actions": [
    {
      "id": "disable",
      "label": "Disable",
      "tone": "danger"
    },
    {
      "id": "revoke",
      "label": "Revoke",
      "tone": "danger"
    },
    {
      "id": "delete",
      "label": "Delete",
      "tone": "danger"
    }
  ]
});
