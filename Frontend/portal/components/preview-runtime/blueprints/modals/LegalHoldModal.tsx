"use client";

import { createModalPart } from "../core";

export const LegalHoldModal = createModalPart({
  "title": "Apply legal hold",
  "description": "Preserve selected records and notify custodians.",
  "eyebrow": "Legal hold",
  "confirmLabel": "Apply hold",
  "style": "danger",
  "requireReason": true,
  "fields": [
    {
      "key": "scope",
      "label": "Scope",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "custodian",
      "label": "Custodian",
      "type": "text"
    }
  ],
  "requireText": "HOLD"
});
