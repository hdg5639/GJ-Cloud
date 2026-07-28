"use client";

import { createModalPart } from "../core";

export const AcknowledgeAlertModal = createModalPart({
  "title": "Acknowledge alert",
  "description": "Confirm ownership and record the triage decision.",
  "eyebrow": "Security",
  "confirmLabel": "Acknowledge",
  "style": "review",
  "requireReason": true,
  "fields": [
    {
      "key": "owner",
      "label": "Owner",
      "type": "text"
    },
    {
      "key": "classification",
      "label": "Classification",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
