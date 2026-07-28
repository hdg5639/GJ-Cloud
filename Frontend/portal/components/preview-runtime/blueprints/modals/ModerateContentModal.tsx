"use client";

import { createModalPart } from "../core";

export const ModerateContentModal = createModalPart({
  "title": "Moderate content",
  "description": "Apply a policy decision to reported content.",
  "eyebrow": "Moderation",
  "confirmLabel": "Apply decision",
  "style": "danger",
  "requireReason": true,
  "fields": [
    {
      "key": "decision",
      "label": "Decision",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "policy",
      "label": "Policy",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
