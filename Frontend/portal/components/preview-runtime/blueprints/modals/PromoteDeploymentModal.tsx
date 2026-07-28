"use client";

import { createModalPart } from "../core";

export const PromoteDeploymentModal = createModalPart({
  "title": "Promote deployment",
  "description": "Move a tested version to the next environment.",
  "eyebrow": "Release management",
  "confirmLabel": "Promote",
  "style": "review",
  "requireReason": false,
  "fields": [
    {
      "key": "environment",
      "label": "Target environment",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
